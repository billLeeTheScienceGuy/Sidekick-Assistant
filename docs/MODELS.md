# Free on-device model shortlist

Researched 2026-10-21; implementation updated 2026-10-22. No inference subscription is needed. Build/download connectivity is different from runtime connectivity. Version 0.2 packages verified Whisper tiny.en weights and their MIT license. Other models remain candidates and are not included.

| Stage | Initial choice | Alternative / rationale | License and source |
|---|---|---|---|
| Speech detection | Silero VAD, ONNX | Small CPU gate; detects speech, not identity. Benchmark noise and false triggers. | MIT: https://github.com/snakers4/silero-vad |
| English transcription | Whisper tiny.en via whisper.cpp | Compare base.en before selecting quality default. tiny is 39M parameters; base is 74M. Smaller downloads do not imply a particular peak RAM. | Upstream Whisper code/weights MIT: https://github.com/openai/whisper ; runtime MIT: https://github.com/ggml-org/whisper.cpp |
| Intent extraction | Qwen3-0.6B, GGUF Q4, non-thinking | Compare Qwen3-1.7B Q4 on difficult promises. Small models may miss context; every result remains a proposal. | Apache-2.0 model cards: https://huggingface.co/Qwen/Qwen3-0.6B and https://huggingface.co/Qwen/Qwen3-1.7B |
| LLM runtime | llama.cpp Android/JNI | GGUF, quantization and schema-constrained generation; same core runtime can support offline evaluation on desktop. | MIT: https://github.com/ggml-org/llama.cpp ; Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md |
| Speaker embeddings (later) | WeSpeaker VoxCeleb ResNet34-LM ONNX | Enroll with multiple samples and evaluate rejection thresholds on unseen speakers/noise. Not authentication and not consent. | Model CC-BY-4.0 (attribution required): https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM |
| Diarization (later) | Evaluate pyannote segmentation-3.0 + embeddings through sherpa-onnx | More compute and integration complexity; not required for first single-speaker demo. Check gated download terms and artifact provenance. | Segmentation model MIT: https://huggingface.co/pyannote/segmentation-3.0 ; runtime Apache-2.0: https://github.com/k2-fsa/sherpa-onnx |

## Why these choices

Native Android has direct access to foreground microphone services and local persistence. whisper.cpp provides an Android sample recommending tiny/base models. Qwen models have permissive licenses and small variants suitable for a first memory/latency experiment. None is declared production-ready for this app without measurements.

Sherpa-onnx is an alternative consolidated audio runtime with Android examples for VAD, ASR and speaker identification. Evaluate it if maintaining multiple JNI adapters becomes costly. Google LiteRT-LM is another viable LLM runtime; Gemma models are free to obtain under separate Gemma terms, whereas Qwen's Apache license simplifies this initial shortlist. Google now directs MediaPipe LLM Inference users toward LiteRT-LM: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android .

Do not rely on Android's generic SpeechRecognizer for all-day offline processing: its documentation notes continuous recognition limitations, and offline recognition availability depends on the installed service. https://developer.android.com/reference/android/speech/SpeechRecognizer

## Model integration contract

For every installed artifact record upstream URL, exact revision, SHA-256, size, license, format, sample rate/context limit, and runtime compatibility. Version 0.2 downloads the model on the developer computer with a checksum-verifying script and bundles it in the APK; first inference verifies/copies it into no-backup app storage. It never downloads weights on the phone. Model contexts are freed after inference and native cancellation is cooperative. There is no cloud fallback.

For extraction require structured output with source evidence, actor, action, raw time phrase, and unresolved fields. Disable Qwen thinking for latency, cap output tokens, and validate model output in code. Preserve source text and timestamps rather than inventing certainty. Do not treat source transcripts as instructions to execute tools.

## Benchmark record (fill on target hardware)

Device: Pixel 7a (Tensor G2, 8 GB RAM), Android 17, ARM64. Whisper tiny.en passed known-speech and cancellation/recovery tests. One known-recording test took about 5.7 seconds including setup/loading. Peak RAM, sustained latency, battery and end-to-end accuracy remain unmeasured. Qwen3 is not integrated. See [validation](VALIDATION.md).

## Shipped artifacts

- whisper.cpp v1.7.6 commit `a8d002cfd879315632a579e73f0148d06959de36`; source ZIP SHA-256 `4f02191b61135324a80137e459cd239e037ef3852bad4e6a2ea69490f3d89490`. CPU-only JNI, two threads; source is fetched during CMake configuration.
- Model: `ggml-tiny.en.bin`, 77,704,715 bytes, GGML format, 16 kHz input. Source: https://huggingface.co/ggerganov/whisper.cpp . SHA-256 `921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f`. The download URL uses main, but any upstream byte change fails checksum validation.
- MIT licenses for Whisper and whisper.cpp are packaged in `assets/licenses/`. Test-only `jfk.wav` comes from the pinned whisper.cpp samples folder and is not included in the main APK.
