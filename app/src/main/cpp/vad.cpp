#include <jni.h>
#include <memory>
#include <mutex>
#include <vector>
#include <algorithm>
#include "whisper.h"

static std::mutex vad_mutex;
static std::unique_ptr<whisper_vad_context, decltype(&whisper_vad_free)> detector(nullptr, whisper_vad_free);
static void quiet_vad(ggml_log_level, const char *, void *) {}
static void vad_error(JNIEnv *env) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Local speech detection failed.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeVad_open(JNIEnv *env, jobject, jstring path) {
    std::lock_guard<std::mutex> lock(vad_mutex);
    const char *raw = env->GetStringUTFChars(path, nullptr);
    if (!raw) return;
    try {
        whisper_log_set(quiet_vad, nullptr);
        auto params = whisper_vad_default_context_params();
        params.n_threads = 1;
        params.use_gpu = false;
        detector.reset(whisper_vad_init_from_file_with_params(raw, params));
        env->ReleaseStringUTFChars(path, raw);
        if (!detector) vad_error(env);
    } catch (...) { env->ReleaseStringUTFChars(path, raw); vad_error(env); }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_sidekick_app_speech_NativeVad_probabilities(JNIEnv *env, jobject, jfloatArray pcm) {
    std::lock_guard<std::mutex> lock(vad_mutex);
    try {
        const int size = env->GetArrayLength(pcm);
        if (!detector || size < 512 || size > 16384) { vad_error(env); return nullptr; }
        std::vector<float> audio(size);
        struct Wipe { std::vector<float> &v; ~Wipe() { std::fill(v.begin(), v.end(), 0.f); } } wipe{audio};
        env->GetFloatArrayRegion(pcm, 0, size, audio.data());
        if (env->ExceptionCheck()) return nullptr;
        if (!whisper_vad_detect_speech(detector.get(), audio.data(), size)) { vad_error(env); return nullptr; }
        const int count = whisper_vad_n_probs(detector.get());
        auto result = env->NewFloatArray(count);
        if (result) env->SetFloatArrayRegion(result, 0, count, whisper_vad_probs(detector.get()));
        return result;
    } catch (...) { vad_error(env); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sidekick_app_speech_NativeVad_close(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(vad_mutex);
    detector.reset();
}
