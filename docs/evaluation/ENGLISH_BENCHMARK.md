# English benchmark (ModelBench)

Generated 2026-11-16 by `scripts/summarize_model_bench.py` from the ModelBench run in `.deps/bench/` (results dated 2026-11-14, laptop, sherpa-onnx JVM).
Metric: **character error rate** = character edits / reference characters (lowercase, no spaces or punctuation). Lower is better.
The number in brackets is the clip count.

| Set | Condition | Parakeet TDT 110M (`parakeet110`) | SenseVoice 2024 (`sense2024`) |
| --- | --- | --- | --- |
| fleurs | clean read speech (LibriSpeech/FLEURS-style clips) | 0.5% (39) | 2.1% (39) |
| scenarios | noisy or far-field scenario clips | 2.0% (117) | 3.2% (117) |
| passages | natural long passages (YouTube captions) | 13.3% (37) | 15.1% (37) |
| wav | app WAV fixtures | 35.1% (3) | 0.0% (3) |

All English models on the same clips:

| Model | fleurs | scenarios | passages |
| --- | --- | --- | --- |
| canary | 1.7% (39) | 3.5% (117) | 28.9% (37) |
| fc20k | 1.2% (39) | 6.0% (117) | 22.2% (37) |
| firered2 | 4.4% (39) | 7.0% (117) | 21.5% (37) |
| funnano | 3.9% (39) | 5.7% (117) | 18.1% (37) |
| nemotron | 1.2% (39) | 5.7% (117) | 20.6% (37) |
| omni1 | 1.6% (39) | 3.7% (117) | 27.9% (37) |
| omni2 | 1.3% (39) | 2.9% (117) | 19.5% (37) |
| parakeet110 | 0.5% (39) | 2.0% (117) | 13.3% (37) |
| parakeet3 | 0.5% (39) | 4.2% (117) | 12.6% (37) |
| qwen3 | 0.5% (39) | 1.2% (117) | 13.5% (37) |
| sense2024 | 2.1% (39) | 3.2% (117) | 15.1% (37) |
| sense2025 | 4.6% (39) | 6.1% (117) | 22.0% (37) |

## Slide wording

"English (Parakeet pack): 0.5% of letters wrong in quiet, 2% noisy or far" matches the `fleurs` and `scenarios` rows above. Say "letters" (character error), not "words". The long natural `passages` set is much harder (see table); do not quote 0.5% for it.
