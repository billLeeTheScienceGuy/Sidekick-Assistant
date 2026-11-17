# SideKick documentation

## Guides: using and testing the app

| Document | What it covers |
|---|---|
| [Try SideKick](guides/TRY_IT.md) | Installing and trying the app on a Pixel 7a |
| [Speech files (model packs)](guides/MODEL_PACKS.md) | Optional offline speech models: what each does, how to install, how to build them |
| [Phone tests that need a person](guides/PHONE_TESTS.md) | Live voice-matching and consent checks, with the `live-check.ps1` script |
| [Demo checklist](guides/DEMO.md) | Supported scope for a demonstration |
| [Hands-off mode](guides/HANDS_OFF.md) | Automatic saving, corrections and Undo |
| [Planner](guides/PLANNER.md) | Reviewing, editing and searching saved plans |
| [Calendar mirroring](guides/CALENDAR_SYNC.md) | Optional one-way sync to the phone calendar |
| [Default assistant & recovery](guides/ASSISTANT_MODE.md) | Resuming listening after restarts and reboots |
| [Local intent model (removed)](guides/INTENT_MODEL.md) | Why the optional Qwen model was removed: rules vs Qwen on the phone |
| [Pixel 7a target](guides/PIXEL_7A.md) | Target device and what has been verified on it |

## Design: how it works

| Document | What it covers |
|---|---|
| [Architecture](design/ARCHITECTURE.md) | Modules, data flow and storage |
| [Foundation plan](design/PLAN.md) | Original stages, evaluation gates and limitations |
| [Roadmap](design/ROADMAP.md) | Remaining work and known limitations |
| [Model shortlist](design/MODELS.md) | Candidate on-device models, licenses and sources |
| [Continuous listening](design/CONTINUOUS_LISTENING.md) | Voice detection, speech windows and activation |
| [Speaker identification](design/SPEAKER_ID.md) · [Speaker turns](design/SPEAKER_TURNS.md) | Voice profiles and separating speakers |
| [Privacy & risk](design/PRIVACY_AND_RISK.md) | Memory controls, retention and scam advisories |
| [Languages](design/LANGUAGES.md) · [Arabic](design/ARABIC.md) · [Multilingual events](design/MULTILINGUAL_EVENTS.md) | Language support and event grammar |
| [Dates](design/DATE_UNDERSTANDING.md) · [Conversation context](design/CONVERSATION_CONTEXT.md) · [Event changes](design/EVENT_CHANGES.md) | Understanding times, follow-ups and corrections |
| [Places & messages](design/PLACES_AND_MESSAGES.md) · [Summaries](design/SUMMARIES.md) · [Reliability](design/RELIABILITY.md) | Venues, incoming messages, daily summaries, end-to-end reliability |

## Evaluation: measurements and limits

| Document | What it covers |
|---|---|
| [Test history and results](evaluation/TEST_HISTORY.md) | Every test so far (T01–T69): method, numbers and what each one made us change |
| [Speaker tuning](evaluation/SPEAKER_TUNING.md) | Owner voice-matching bars: public-voice tuning, phone replay, chosen values |
| [Validation](evaluation/VALIDATION.md) | Checks performed on device and in tests |
| [Speech recognition (ASR) evaluation](evaluation/ASR_EVALUATION.md) · [Speech quality](evaluation/SPEECH_QUALITY.md) | Model routing and recognition accuracy |
| [Recognition reviews](evaluation/RECOGNITION_033.md) ([0.33.1](evaluation/RECOGNITION_0331.md), [0.34](evaluation/RECOGNITION_034.md), [next steps](evaluation/RECOGNITION_NEXT_STEPS.md)) | Version-by-version recognition findings |
| [Robustness](evaluation/ROBUSTNESS_EVALUATION.md) · [Playback](evaluation/PLAYBACK_EVALUATION.md) · [Sustained turns](evaluation/SUSTAINED_TURNS.md) | Noisy speech, replayed audio and long sessions |
| [Text-understanding accuracy](evaluation/ACCURACY_REPORT.md) | 92-case curated evaluation of event extraction |
| [Live transcription](evaluation/STREAMING_BENCHMARK.md) · [Interface](evaluation/INTERFACE_BENCHMARK.md) | Latency and interface layout checks |
| [Evaluation data](evaluation/README.md) | Public fixtures and result files |

## Project

| Document | What it covers |
|---|---|
| [Capstone checklist](project/CAPSTONE_CHECKLIST.md) | Acceptance goals |
| [Submission report](project/SUBMISSION_REPORT.md) | Implementation and evaluation summary |
| [Development log](project/DEVELOPMENT_LOG.md) | Dated progress notes |
