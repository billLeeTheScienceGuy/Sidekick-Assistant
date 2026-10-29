#include <jni.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "whisper.h"

static std::atomic_bool cancelled{false};
static std::mutex inference_mutex;
static std::unique_ptr<whisper_context, decltype(&whisper_free)> cached_context(nullptr, whisper_free);
static std::string cached_path;
static bool abort_inference(void *) { return cancelled.load(); }
static void quiet_log(ggml_log_level, const char *, void *) {}
static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeSpeech_cancel(JNIEnv *, jobject) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeSpeech_prepare(JNIEnv *, jobject) { cancelled.store(false); }
extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeSpeech_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    cached_context.reset();
    cached_path.clear();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sidekick_app_speech_NativeSpeech_transcribe(JNIEnv *env, jobject, jstring path, jfloatArray pcm, jint threads, jboolean retain_model, jint audio_context, jstring language, jint beam_size) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    try {
        if (threads < 1 || threads > 4) { fail(env, "Invalid speech thread count."); return nullptr; }
        if (beam_size < 1 || beam_size > 5) { fail(env, "Invalid speech beam size."); return nullptr; }
        const char *raw_language = env->GetStringUTFChars(language, nullptr);
        if (!raw_language) return nullptr;
        std::string language_code(raw_language);
        env->ReleaseStringUTFChars(language, raw_language);
        if (language_code != "auto" && language_code != "en" && language_code != "ko" && language_code != "zh" &&
            language_code != "fr" && language_code != "de" && language_code != "ru" && language_code != "ar") {
            fail(env, "Unsupported speech language."); return nullptr;
        }
        const auto count = env->GetArrayLength(pcm);
        if (audio_context < 0 || audio_context > 1500 || (audio_context && count > audio_context * 320)) {
            fail(env, "Audio exceeds the configured encoder window."); return nullptr;
        }
        if (count < 1600 || count > 16000 * 30) { fail(env, "Audio must be between 0.1 and 30 seconds."); return nullptr; }
        std::vector<float> samples(count);
        struct WipeSamples {
            std::vector<float> &samples;
            ~WipeSamples() { std::fill(samples.begin(), samples.end(), 0.0f); }
        } wipe{samples};
        env->GetFloatArrayRegion(pcm, 0, count, samples.data());
        if (env->ExceptionCheck()) return nullptr;
        const char *raw_path = env->GetStringUTFChars(path, nullptr);
        if (!raw_path) return nullptr;
        std::string model_path(raw_path);
        env->ReleaseStringUTFChars(path, raw_path);
        whisper_log_set(quiet_log, nullptr); // Never log private audio/transcripts.
        auto cp = whisper_context_default_params();
        cp.use_gpu = false;
        if (!retain_model || cached_path != model_path) cached_context.reset();
        if (!cached_context) {
            cached_context.reset(whisper_init_from_file_with_params(model_path.c_str(), cp));
            cached_path = model_path;
        }
        // Scope cleanup also applies to errors and cancellation in one-shot mode.
        struct Cleanup { bool retain; ~Cleanup() { if (!retain) cached_context.reset(); } } cleanup{bool(retain_model)};
        auto *context = cached_context.get();
        if (!context) { fail(env, "Could not load the local speech model."); return nullptr; }
        auto p = whisper_full_default_params(beam_size == 1 ? WHISPER_SAMPLING_GREEDY : WHISPER_SAMPLING_BEAM_SEARCH);
        p.beam_search.beam_size = beam_size;
        p.n_threads = threads;
        p.audio_ctx = audio_context;
        p.language = language_code == "auto" ? nullptr : language_code.c_str();
        p.translate = false;
        p.no_context = true;
        p.print_progress = false;
        p.print_realtime = false;
        p.print_timestamps = false;
        p.print_special = false;
        p.no_timestamps = true;
        p.temperature_inc = 0.0f;
        p.abort_callback = abort_inference;
        const int status = whisper_full(context, p, samples.data(), count);
        std::fill(samples.begin(), samples.end(), 0.0f);
        if (cancelled.load()) { fail(env, "Transcription cancelled."); return nullptr; }
        if (status != 0) { fail(env, "Local transcription failed."); return nullptr; }
        std::string text;
        for (int i = 0; i < whisper_full_n_segments(context); ++i) {
            if (whisper_full_get_segment_no_speech_prob(context, i) < 0.6f)
                text += whisper_full_get_segment_text(context, i);
        }
        auto result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
        return result;
    } catch (const std::exception &) { fail(env, "Speech engine failed. Try a shorter recording."); return nullptr; }
}
