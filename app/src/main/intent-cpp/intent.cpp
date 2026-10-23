#include <jni.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

static std::mutex gate;
static std::atomic_bool cancelled{false};
static bool abort_intent(void *) { return cancelled.load(); }
static void quiet(ggml_log_level, const char *, void *) {}
static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
static std::string utf8(JNIEnv *env, jbyteArray bytes) {
    const int size = env->GetArrayLength(bytes);
    std::string result(size, '\0');
    env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_intent_NativeIntent_cancel(JNIEnv *, jobject) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_intent_NativeIntent_prepare(JNIEnv *, jobject) { cancelled.store(false); }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sidekick_app_intent_NativeIntent_extract(JNIEnv *env, jobject, jstring path, jbyteArray input, jbyteArray grammar_bytes) {
    std::lock_guard<std::mutex> lock(gate);
    try {
        auto prompt = utf8(env, input);
        auto grammar = utf8(env, grammar_bytes);
        if (env->ExceptionCheck()) return nullptr;
        if (prompt.size() > 16000 || grammar.size() > 8000) { fail(env, "Input is too long."); return nullptr; }
        llama_log_set(quiet, nullptr);
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_backend_init(); });
        const char *raw = env->GetStringUTFChars(path, nullptr);
        if (!raw) return nullptr;
        std::string model_path(raw);
        env->ReleaseStringUTFChars(path, raw);
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.use_mmap = true;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
            llama_model_load_from_file(model_path.c_str(), mp), llama_model_free);
        if (!model) { fail(env, "Could not load the installed intent model."); return nullptr; }
        const auto *vocab = llama_model_get_vocab(model.get());
        const int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, false, true);
        if (count < 1 || count > 1600) { fail(env, "Use a shorter note (at most 1600 input tokens)."); return nullptr; }
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, false, true) < 0) {
            fail(env, "Could not read the note."); return nullptr;
        }
        auto cp = llama_context_default_params();
        cp.n_ctx = 2048;
        cp.n_batch = 256;
        cp.n_ubatch = 128;
        cp.n_threads = 2;
        cp.n_threads_batch = 2;
        cp.no_perf = true;
        cp.abort_callback = abort_intent;
        std::unique_ptr<llama_context, decltype(&llama_free)> context(llama_init_from_model(model.get(), cp), llama_free);
        if (!context) { fail(env, "Not enough memory for local analysis."); return nullptr; }
        for (int offset = 0; offset < count; offset += 256) {
            if (cancelled.load() || llama_decode(context.get(), llama_batch_get_one(tokens.data() + offset, std::min(256, count - offset)))) {
                fail(env, "Analysis cancelled or interrupted."); return nullptr;
            }
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        auto *constraint = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (!constraint) { fail(env, "Invalid extraction grammar."); return nullptr; }
        llama_sampler_chain_add(sampler.get(), constraint);
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
        std::string output;
        for (int i = 0; i < 384; ++i) {
            if (cancelled.load()) { fail(env, "Analysis cancelled."); return nullptr; }
            auto token = llama_sampler_sample(sampler.get(), context.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) {
                auto bytes = env->NewByteArray(output.size());
                if (bytes) env->SetByteArrayRegion(bytes, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
                return bytes;
            }
            std::vector<char> piece(256);
            int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (length < 0) {
                piece.resize(-length);
                length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (length < 0) { fail(env, "Invalid model token."); return nullptr; }
            output.append(piece.data(), length);
            if (output.size() > 8000 || llama_decode(context.get(), llama_batch_get_one(&token, 1))) {
                fail(env, "Analysis interrupted or exceeded its output limit."); return nullptr;
            }
        }
        fail(env, "Analysis exceeded its token limit. Try a shorter note.");
    } catch (...) { fail(env, "Local intent analysis failed."); }
    return nullptr;
}
