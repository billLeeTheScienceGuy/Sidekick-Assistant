#include <jni.h>
#include <atomic>
#include <memory>
#include <string>
#include <vector>
#include "whisper.h"

static std::atomic_bool cancelled{false};
static bool abort_inference(void *) { return cancelled.load(); }
static void quiet_log(ggml_log_level, const char *, void *) {}
static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeSpeech_cancel(JNIEnv *, jobject) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeSpeech_prepare(JNIEnv *, jobject) { cancelled.store(false); }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sidekick_app_speech_NativeSpeech_transcribe(JNIEnv *env, jobject, jstring path, jfloatArray pcm) {
    try {
        const auto count = env->GetArrayLength(pcm);
        if (count < 1600 || count > 16000 * 30) { fail(env, "Audio must be between 0.1 and 30 seconds."); return nullptr; }
        std::vector<float> samples(count);
        env->GetFloatArrayRegion(pcm, 0, count, samples.data());
        if (env->ExceptionCheck()) return nullptr;
        const char *raw_path = env->GetStringUTFChars(path, nullptr);
        if (!raw_path) return nullptr;
        std::string model_path(raw_path);
        env->ReleaseStringUTFChars(path, raw_path);
        whisper_log_set(quiet_log, nullptr); // Never log private audio/transcripts.
        auto cp = whisper_context_default_params();
        cp.use_gpu = false;
        std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
            whisper_init_from_file_with_params(model_path.c_str(), cp), whisper_free);
        if (!context) { fail(env, "Could not load the local speech model."); return nullptr; }
        auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.n_threads = 2;
        p.language = "en";
        p.translate = false;
        p.no_context = true;
        p.print_progress = false;
        p.print_realtime = false;
        p.print_timestamps = false;
        p.print_special = false;
        p.no_timestamps = true;
        p.temperature_inc = 0.0f;
        p.abort_callback = abort_inference;
        const int status = whisper_full(context.get(), p, samples.data(), count);
        std::fill(samples.begin(), samples.end(), 0.0f);
        if (cancelled.load()) { fail(env, "Transcription cancelled."); return nullptr; }
        if (status != 0) { fail(env, "Local transcription failed."); return nullptr; }
        std::string text;
        for (int i = 0; i < whisper_full_n_segments(context.get()); ++i) {
            if (whisper_full_get_segment_no_speech_prob(context.get(), i) < 0.6f)
                text += whisper_full_get_segment_text(context.get(), i);
        }
        auto result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
        return result;
    } catch (const std::exception &) { fail(env, "Speech engine failed. Try a shorter recording."); return nullptr; }
}
