# Local intent model (removed)

SideKick 0.4 added an optional on-device language model, Qwen3-0.6B Q4_K_M through llama.cpp, for typed notes
("Find commitments"). Live speech always used the local rules. In November 2026 the model was compared with the rules
on the phone and removed. On next start the app deletes a previously imported model file (484 MB).

## Comparison (November 16, 2026)

- **Device:** Pixel 7a, isolated benchmark package (never the personal app). Model imported into the test package only.
- **Data:** the 92 self-authored cases of `core/src/test/resources/evaluation.tsv` (7 languages; 62 baseline and
  30 challenge cases), scored like `AccuracyEvaluationTest`. Others' speech never reached the model, so OTHER rows used
  the rules in every mode.
- **Modes:** rules only; Qwen only (the whole text to the model); rules + Qwen (the app's path: Qwen only on text the
  rules left unresolved).

| Mode | Found (TP / FP / FN) | Precision | Recall | Exact time | Time per sentence |
| --- | --- | --- | --- | --- | --- |
| Rules | 82 / 0 / 1 | 100.0% | 98.8% | 100.0% | 7 ms median |
| Qwen only | 19 / 1 / 64 | 95.0% | 22.9% | 94.7% | 14.4 s median, 15.5 s p90, 58.8 s max; 4 of 85 calls failed (token limit) |
| Rules + Qwen | 82 / 0 / 1 | 100.0% | 98.8% | 100.0% | Qwen ran on 1 sentence and added nothing |

Owner sentences by language, Qwen only: English 12 of 22, Korean, Chinese, French, German, Russian and Arabic 0 of 9
each (rules: all found except one Arabic sentence).

## Decision

Decision rule agreed beforehand: keep Qwen as an optional download only if it performs better; drop it if the
difference is negligible. Qwen found nothing the rules missed, was about 2,000 times slower and needed 484 MB, so it
was removed (code, native llama.cpp build, import screen and tests).

**Limit:** the cases are self-authored and mostly use phrasings the rules support. Free-form speech was not tested;
this comparison shows no evidence that Qwen would help there.
