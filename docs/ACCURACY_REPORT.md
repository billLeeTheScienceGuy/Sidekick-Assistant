# Text-understanding accuracy evaluation

92 self-authored cases across seven languages. Fixed capture: 2026-11-08T15:00:00Z; timezone: America/New_York.
Corpus SHA-256: `264a4d7dc51e50ca0b3221a76586bc75c9a5f543061e9aa7899c72aa24c28c99`.

This tests local text extraction with supplied speaker labels, not speech recognition, speaker ID, optional Qwen, live correction execution or a representative user population. Baseline cases exercise supported grammar; challenge cases test broader wording. Both are reported, including failures. No raw user conversations are used.

Event matches use the labeled subject token. Time, attendance and place accuracy are on matched events; missed events are counted separately. Time accuracy includes unknown-time preservation and known end times. False reminders are reminders where the label says none; mistimed reminders have an incorrect start; missed reminders include missed events. This is not a calibrated confidence score.

| Group | Cases | TP / FP / FN | Precision | Recall | Exact time | Attendance | Known place | False / mistimed / missed reminders |
|---|---:|---|---:|---:|---:|---:|---:|---:|
| All | 92 | 80 / 0 / 3 | 100.0% | 96.4% | 98.8% | 92.5% | 100.0% | 0 / 0 / 10 |
| Baseline | 62 | 55 / 0 / 0 | 100.0% | 100.0% | 100.0% | 100.0% | 100.0% | 0 / 0 / 0 |
| Challenge | 30 | 25 / 0 / 3 | 100.0% | 89.3% | 96.0% | 76.0% | n/a | 0 / 0 / 10 |
| en | 26 | 23 / 0 / 0 | 100.0% | 100.0% | 95.7% | 95.7% | 100.0% | 0 / 0 / 2 |
| ko | 11 | 10 / 0 / 0 | 100.0% | 100.0% | 100.0% | 100.0% | 100.0% | 0 / 0 / 0 |
| zh | 11 | 9 / 0 / 1 | 100.0% | 90.0% | 100.0% | 100.0% | 100.0% | 0 / 0 / 1 |
| fr | 11 | 10 / 0 / 0 | 100.0% | 100.0% | 100.0% | 80.0% | 100.0% | 0 / 0 / 2 |
| de | 11 | 9 / 0 / 1 | 100.0% | 90.0% | 100.0% | 88.9% | 100.0% | 0 / 0 / 2 |
| ru | 11 | 10 / 0 / 0 | 100.0% | 100.0% | 100.0% | 90.0% | 100.0% | 0 / 0 / 1 |
| ar | 11 | 9 / 0 / 1 | 100.0% | 90.0% | 100.0% | 88.9% | n/a | 0 / 0 / 2 |

## Cases needing work

- ch01 (en, challenge): wrong/unknown time for party; missing reminder
- ch02 (en, challenge): wrong attendance for maya; missing reminder
- ch07 (zh, challenge): missed 聚会; missing reminder
- ch08 (fr, challenge): wrong attendance for anniversaire; missing reminder
- ch09 (de, challenge): missed geburtstag; missing reminder
- ch11 (ar, challenge): missed حفلة; missing reminder
- ex13 (fr, challenge): wrong attendance for rendez-vous; missing reminder
- ex15 (de, challenge): wrong attendance for termin; missing reminder
- ex17 (ru, challenge): wrong attendance for приём; missing reminder
- ex19 (ar, challenge): wrong attendance for موعد; missing reminder

Reproduce with `gradlew :core:test --tests '*AccuracyEvaluationTest'`. The full case text and labels are in `core/src/test/resources/evaluation.tsv`. The generated report is `core/build/reports/accuracy.md`. Do not present these curated results as real-world accuracy or hide the challenge results.
