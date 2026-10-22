#include <jni.h>
#include <chrono>
#include <memory>
#include <vector>
#include <algorithm>
#include "whisper.h"

static void quiet(ggml_log_level, const char *, void *) {}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_sidekick_app_VadBenchmarkNative_measure(JNIEnv *env, jobject, jstring path, jfloatArray pcm) {
    try {
        using clock = std::chrono::steady_clock;
        auto milliseconds = [](clock::time_point start) {
            return std::chrono::duration<double, std::milli>(clock::now() - start).count();
        };
        const int count = env->GetArrayLength(pcm);
        if (count < 512 || count > 16000 * 30) return nullptr;
        std::vector<float> samples(count);
        env->GetFloatArrayRegion(pcm, 0, count, samples.data());
        if (env->ExceptionCheck()) return nullptr;
        const char *raw = env->GetStringUTFChars(path, nullptr);
        if (!raw) return nullptr;
        whisper_log_set(quiet, nullptr);
        auto params = whisper_vad_default_context_params();
        params.n_threads = 1;
        params.use_gpu = false;
        const auto load_start = clock::now();
        std::unique_ptr<whisper_vad_context, decltype(&whisper_vad_free)> ctx(
            whisper_vad_init_from_file_with_params(raw, params), whisper_vad_free);
        env->ReleaseStringUTFChars(path, raw);
        if (!ctx) return nullptr;
        double values[5] = {milliseconds(load_start), 0, 0, 0, 0};
        for (int run = 0; run < 20; ++run) {
            const auto start = clock::now();
            if (!whisper_vad_detect_speech(ctx.get(), samples.data(), count)) return nullptr;
            const auto elapsed = milliseconds(start);
            values[1] += elapsed / 20;
            values[2] = std::max(values[2], elapsed);
        }
        const auto probabilities = whisper_vad_probs(ctx.get());
        const int frames = whisper_vad_n_probs(ctx.get());
        for (int i = 0; i < frames; ++i) {
            values[3] += (probabilities[i] >= .5f ? 1.0 : 0.0) / frames;
        }
        values[4] = frames;
        std::fill(samples.begin(), samples.end(), 0.0f);
        auto result = env->NewDoubleArray(5);
        if (result) env->SetDoubleArrayRegion(result, 0, 5, values);
        return result;
    } catch (...) { return nullptr; }
}
