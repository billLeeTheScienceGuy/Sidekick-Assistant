# Scam-warning rules: evaluation on public data

Rules under test: `core/src/main/kotlin/com/sidekick/core/ScamSignals.kt` (the real Kotlin code, not a copy).
Honest summary: on real data the rules catch little of what is labelled "scam" outside Korean SMS, and false alarms on everyday text are very low.
Earlier numbers (40/28 and 18/16 lines) were on lines written by us; this is the first test on third-party data.

## Datasets (stored only under `.deps/scam-data/`, git-ignored, never committed)

| Dataset | Lang | Real / synthetic | License (as listed) | Used as |
|---|---|---|---|---|
| UCI SMS Spam (`ucirvine/sms_spam`) | en | real SMS | listed "unknown" (UCI: CC BY 4.0) | spam vs ham; 5.0k unique |
| SMS Spam Multilingual (`dbarbedillo/...`) | de fr zh ar ru ko | machine translation of UCI | GPL | spam vs ham (translations are poor) |
| Korean smishing (`jmjmjm3/kor-smishing-message`) | ko | real SMS | CC BY-NC-SA 4.0 | smishing vs "정상" (normal) |
| Korean voice-phishing (`HyaDoo/ko-voicephishing-binary-classification-ver2`) | ko | ASR call-transcript chunks, provenance unclear | Apache-2.0 | scam vs normal chunks; label noise (see gaps) |
| Chinese anti-fraud calls (`leon202508/cn-antifraud`) | zh | real call ASR | Apache-2.0 | 102 fraud / 100 normal calls |
| BothBosu scam-dialogue, Scammer-Conversation | en | LLM-generated | Apache-2.0 | scam vs normal dialogues |
| BothBosu youtube-scam-conversations | en | real recorded scam-baiting calls (20) | Apache-2.0 | scam only |
| DailyDialog (`roskoN/dailydialog`) | en | written dialogues | CC BY-NC-SA 4.0 | everyday |
| Tatoeba (`mteb/tatoeba-bitext-mining`) | 7 langs | real sentences | CC BY 2.0 | everyday, 1000/lang |
| OpenSubtitles lines (`sentence-transformers/parallel-sentences-opensubtitles`) | ko zh ar de fr ru en | real movie lines | none stated (OPUS) | everyday |
| Russian forum chat (`benzlokzik/russian-spam-fork`, non-spam rows) | ru | real | CC BY-NC 4.0 | everyday |
| French / German synthetic spam (Kinoux, tanaos) | fr de | LLM-generated | custom / MIT | spam vs ham, weak: "spam" is not necessarily scam |

Not usable without login (gated): OpenSubtitles2024, a Chinese chat set. No real scam data was found for ru, ar, fr, de.

## Method
- `scripts/laptop-eval/scam_prepare.py` builds one file; the 50/50 dev/test split is SHA-1 of a fixed seed plus the normalised text (or conversation id), fixed before any result was seen. Duplicates are kept once; translations of one SMS share a split. Template-like scams make near-duplicates across splits, so test is not fully independent of dev.
- `core` test `ScamCorpusEvalTest` (run with `SCAM_CORPUS=1`) runs the real rules: conversations go through `ScamMonitor` one utterance at a time (5 s apart), messages through `ScamCheck.message`. Conversations with two speakers are also run on the phone owner's side only ("owner"), which is what the app hears on a call.
- Rules were changed using dev errors only (rules: new signs NOTICE, PERSONAL_INFO, CLAIM; more Korean/Chinese/English patterns; Korean awareness notices and code messages no longer count; link plus pressure alone is not a warning; more risky link endings and random-looking link names). Test was run once at the end. The ScamSignalsTest sets still pass (held-out: 17/18 warned, 0/16 false alarms).
- Recall = scam items reaching CAUTION or STOP; STOP column in the raw output. False alarm = ordinary items reaching CAUTION or STOP at any step.

## Results (TEST split; baseline before changes -> final)

| Dataset (lang) | Recall any, baseline | Recall any, final | Recall STOP, final | False alarm, baseline | False alarm, final |
|---|---|---|---|---|---|
| Korean smishing SMS (ko) | 2.3% (63/2710) | 44.2% (1199/2710) | 44.2% | 1.1% (64/5935) | 0.4% (23/5935) |
| Korean voice-phishing chunks (ko) | 0.5% (23/4429) | 1.2% (55/4429) | 0.7% | 0.0% (0/5018) | 0.0% (0/5018) |
| Chinese fraud calls (zh) | 0% (0/49) | 14.3% (7/49) | 14.3% | 0% (0/47) | 2.1% (1/47) |
| LLM scam dialogues (en, all speakers) | 14.6% (62/426) | 64.6% (275/426) | 58.9% | 0% (0/371) | 0% (0/371) |
| same, phone owner's side only | 5.2% | 15.3% | 8.0% | 0% | 0% |
| LLM Scammer-Conversation (en) | 0% | 62.7% (96/153) | 34.6% | 0% | 0% |
| Real scam-baiting calls (en, 13) | 23% | 31% (4/13) | 7.7% | - | - |
| UCI SMS spam (en) | 0.3% (1/302) | 1.7% (5/302) | 0% | 0% (0/2212) | 0% (0/2212) |
| DailyDialog (en, everyday) | - | - | - | 0.1% (5/5565) | 0.1% (5/5565); 0.06% of utterance windows |
| Russian forum (ru) | 0% (MT SMS) | 0% | 0% | 0.1% (1/1998) | 0.1% (1/1998) |
| French, German, Arabic, Russian MT SMS spam | 0% | 0% (de synthetic: 1.7%) | - | 0 | 0 |

Everyday false alarms on test (final): subtitles, Tatoeba, MT SMS ham in all seven languages 0 alarms in about 25k lines; Korean normal call chunks 0/5018; English 5/10173 (0.05%); Chinese 1/3525.
Dev numbers were the same within noise (for example Korean smishing 45.0% recall, 0.5% false alarm; LLM dialogues 60.3%; Chinese 22.6%).

## Known gaps
- Recall is low except for Korean smishing and generic LLM-style scripts. UCI "spam" is mostly premium-rate marketing, not scams, and the rules do not target it. The Korean voice-phishing chunks are labelled per call, not per chunk: only about 3% of "scam" chunks contain both a crime story and an ask, and many are plainly ordinary insurance or bank talk, so the label is noisy and recall there cannot be high.
- The Korean "normal" SMS set contains real government and company notices with links (pension, health, parcels); they look like smishing, so a few false alarms (0.4%) are unavoidable for message previews. This is higher than the 0.5% goal for nothing else; everyday conversation stays far below it.
- No real data for ru, ar, fr, de scams, so those languages are untested for recall and were not tuned. Chinese has only 49 test calls. The dialogue sets are LLM-written; the owner-only view (the app hears one side) is much worse, about 15%.
- Short transcript chunks, machine-translated SMS and the shared dev/test near-duplicates limit how far these numbers generalise. Rules are regexes and are conservative by design.

## Owner's side only: second round (phone calls)

On a call the app hears only the phone owner, so the rules were extended to what the owner says: repeating the caller's claim ("you're from the bank?", "my account is locked?"), asking why a number, code or money is needed ("why do you need my social security number?"), mentioning gift cards, crypto or a code, going along ("let me get my card", "which account do I send it to?") and confusion about secrecy ("why do I have to keep it secret?"). Patterns are in `ScamSignals.kt`, in all seven languages; only English was tuned on data (dev), the other six are short and conservative and have no recall data.

What changed in the code:
- Two new weak signs, `OWNER_STORY` and `OWNER_ASK`. They never count as a story or an ask, never give STOP, and each sentence counts once however often it is heard again (the app re-sends recent text). Sentences that only talk about a scam ("I was expecting you to ask for my number", "if I were to sign up") are ignored by weak signs.
- `ScamAssessment.of(signals, inCall)` and `ScamMonitor.add/assessment(..., inCall)`: in a call, two weak signs, or one weak sign next to a real story or ask, give CAUTION; outside a call it takes four weak signs in the 10-minute window. Existing rules are untouched.
- App: `LocalRiskAlerts.inspect(text, inCall)`; `ListeningService` passes `callActive`. No new user-visible text (the reasons are the existing English labels).

Dev was used for all tuning (weak patterns, threshold, hedged-sentence filter, removing "money order", "prepaid" and "taxes" after they alarmed on DailyDialog); TEST was run once at the end.

| TEST, owner's side only | Baseline | Final, no call flag | Final, call mode |
|---|---|---|---|
| LLM scam dialogues (en, 426), recall any | 15.3% (65) | 19.7% (84) | 59.4% (253) |
| same, recall STOP | 8.0% | 8.0% | 8.0% |
| Real scam-baiting calls (en, 13), recall any | 23.1% (3) | 23.1% (3) | 38.5% (5) |
| LLM Scammer-Conversation (en, 153) | 0% | 0% | 0% |
| False alarms, ordinary LLM dialogues (371), owner side | 0 | 0 | 1 (0.3%) |
| False alarms, DailyDialog one speaker (5,565), "normal call" proxy | 2 | 2 | 2 |

Everyday false alarms on TEST (default, no call flag), before -> after: English 5/10173 -> 5, Korean 23/14657 -> 23, Chinese 1/3525 -> 1, Russian 1/5931 -> 1, Arabic, French, German 0 -> 0. The same everyday lines run as one utterance with the call flag on: unchanged except one French synthetic "ham" line (1/267, not an everyday set). Whole dialogues with the call flag (both speakers, which the app does not hear on a call): DailyDialog 5 -> 7 of 5,565. Dev: owner recall 15.8% -> 19.0% (call 58.4%), DailyDialog owner 4 -> 4.
The DailyDialog 2 owner-side false alarms were already there before this round (existing rules).

Adopted: call mode, because it gave 3.9 times the owner-side recall with no everyday false alarm in the measured sets.

Gaps: the LLM victims are mostly sceptical ("how do I know this is legit?") so the best recall is still on dialogues where the owner mentions the ask; the Scammer-Conversation owner side stays at 0% (the owner only questions the caller). The 1 extra false alarm in call mode is an ordinary dialogue where the owner discusses a credit card. In a real call the owner may speak about their own bank in good faith, so call-mode cautions will occur in genuine bank calls (a caution, never STOP). Real data exists only for English dialogues, Korean and Chinese (not as two-sided calls), so the six other languages are untested for owner-side recall; the LLM dialogues are written, not recorded; 13 real calls is a very small sample.

## Rerun
```
.deps/python312/python.exe -I scripts/laptop-eval/scam_prepare.py   # needs the downloads in .deps/scam-data
export JAVA_HOME=C:/Users/Patron/.jdks/jbr-17.0.14
.deps/python312/python.exe -I scripts/laptop-eval/scam_eval.py --run --tag mine --splits dev   # ~2 min
.deps/python312/python.exe -I scripts/laptop-eval/scam_eval.py --tag mine --errors 20 --ds kor-smishing --kind miss   # dev errors only
```
