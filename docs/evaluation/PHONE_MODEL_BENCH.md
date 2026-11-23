# Speech models on the phone (November 18, 2026)

16 speech models run on the Pixel itself (`PhoneModelBenchTest`, set `phonebench`): 20 noisy everyday clips per
language from the scenario set (140 clips, average 9.6 s of audio each), 4 threads, one model per process, phone
locked and unplugged. Score: character error rate (CER, lower is better). Seconds = time per clip on the phone (a clip
is about 9.6 s of speech, so 9.6 s per clip means it keeps up with speech in real time). Reproduce:
`scripts/phone-tests/phonebench_report.py`.

| Model (size) | English | Korean | Chinese | French | German | Russian | Arabic |
|---|---|---|---|---|---|---|---|
| **In the app** | | | | | | | |
| Parakeet 110M (137 MB) | **1.4% · 0.4 s** | | | | | | |
| SenseVoice 2024 (240 MB) | 2.4% · 0.8 s | **10.3% · 1.2 s** | 19.6% · 1.1 s | | | | |
| Omni v2 (366 MB) | 2.6% · 4.3 s | 21.9% · 6.4 s | 36.1% · 5.7 s | 15.8% · 6.0 s | 11.4% · 5.8 s | 16.1% · 5.7 s | 14.3% · 6.4 s |
| GigaAM v3 Russian (229 MB) | | | | | | **2.8% · 1.4 s** | |
| Korean Zipformer (77 MB) | | 53.8% · 0.6 s | | | | | |
| **Candidates** | | | | | | | |
| Qwen3-ASR 0.6B (954 MB) | 0.8% · 5.7 s | 14.5% · 10.1 s | **14.4% · 6.9 s** | **11.9% · 9.6 s** | 14.5% · 9.0 s | 19.3% · 10.0 s | 17.9% · 9.6 s |
| Cohere Transcribe (2.9 GB) | crashed | crashed | crashed | crashed | **3.9% · 30.2 s** | not supported | **11.7% · 12.6 s** |
| Nemotron 3.5 0.6B (653 MB) | 2.7% · 5.2 s | 29.7% · 7.8 s | 41.9% · 7.2 s | 21.0% · 7.2 s | 14.1% · 6.9 s | 22.3% · 7.3 s | 13.3% · 7.6 s |
| Parakeet 0.6B v3 (641 MB) | 4.3% · 1.6 s | | | 20.7% · 2.4 s | 16.6% · 2.1 s | 15.8% · 2.1 s | |
| Canary 180M (198 MB) | 0.8% · 2.6 s | | | 24.8% · 3.7 s | 21.7% · 3.3 s | | |
| Fast Conformer 20k (134 MB) | 3.7% · 0.4 s | | | 22.1% · 0.6 s | 17.3% · 0.6 s | 20.4% · 0.6 s | |
| FunASR Nano (254 MB) | 3.2% · 0.8 s | 44.4% · 1.3 s | 18.2% · 1.1 s | | | | |
| FireRed ASR2 (742 MB) | 4.9% · 4.1 s | | 26.2% · 5.7 s | | | | |
| SenseVoice 2025 (233 MB) | 6.3% | 83.8% | 22.2% | | | | |
| Moonshine Arabic (135 MB) | | | | | | | 91.8% |

Omni v1 and v2 gave identical results (the app's Omni file is v2). Cohere ran Arabic and German, then the phone ran out
of memory when the test loaded a third copy of the 2.9 GB model for English (the test kept one copy per language); it
is in any case three times slower than speech in German.

**What this means**
- English, Korean and Russian: the app's models are already the best choice (most accurate or within a point, and
  fastest).
- Chinese and French: Qwen3-ASR is clearly more accurate (Chinese 19.6% -> 14.4%, French 15.8% -> 11.9%) but takes
  7-10 s per 10-second phrase and 954 MB. Candidate for an optional "best accuracy" file; needs a live-use check.
- German: Cohere Transcribe is far more accurate (11.4% -> 3.9%) but too slow (30 s for 10 s of speech) and too large
  for this phone. Not usable today.
- Arabic: small gains only (Cohere 11.7%, Nemotron 13.3% vs Omni 14.3%); not worth 0.65-2.9 GB.
