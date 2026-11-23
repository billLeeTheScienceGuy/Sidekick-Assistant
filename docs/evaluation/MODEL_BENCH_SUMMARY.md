# Model benchmark summary (ModelBench)

Generated 2026-11-16 by `scripts/summarize_model_bench.py` from `.deps/bench/` (laptop run, results dated 2026-11-14).

- **Models compared:** 16 (canary, fc20k, firered2, funnano, gigaam3, lid, moonar, nemotron, omni1, omni2, parakeet110, parakeet3, qwen3, sense2024, sense2025, zipko; `lid` is the Whisper-tiny language identifier).
- **Distinct test clips:** 1377 (each model runs only on the languages it supports).
- **Scored model-clip results:** 12126 (before the natural-caption filter), 11281 after it.

| Language | Clips |
| --- | --- |
| ar | 250 |
| de | 161 |
| en | 204 |
| fr | 184 |
| ko | 198 |
| ru | 199 |
| zh | 181 |

| Set | Clips |
| --- | --- |
| codeswitch-tts | 40 |
| cs-fleurs | 180 |
| fleurs | 399 |
| lebanese | 37 |
| mixed | 56 |
| passages | 270 |
| scenarios | 387 |
| wav | 8 |

README wording "benchmarking 16 models on about 1,380 clips": 16 = the model list above (15 recognizers + the language identifier); the clip total is the distinct-clip count in this table. Update the README if the numbers here differ.
