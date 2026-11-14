# Recognition follow-up: 0.33.1

The real Korean–English microphone trial on 0.33.0 did not pass: all five voice chunks were unassigned, several words and the time correction were garbled, and the expected event was not saved. Synthetic/public fixture results did not predict this real conversation result.

## Changes

- In Auto-detect, speaker matching checks all explicitly enrolled language profiles for the selected speaker model. One complete turn must match one profile; windows from different profiles cannot be combined. Existing thresholds, short-phrase anchor requirements and disabled-matching behavior remain in place. Manual language selection continues to use its own profile.
- An experimental automatic second reading for longer CJK/English phrases was evaluated, then removed: the twelve synthetic bilingual clips remained at 7/12 venue preservation and 17.44% character error, while aggregate decoding time rose from 46.2 to 60.6 seconds. The shipped speech routing remains the 0.33.0 routing. This patch does not claim improved natural bilingual transcription.
- No new model download, cloud transcription, audio persistence or automatic voice enrollment.

## Validation

Core and isolated Pixel profile-routing tests passed. The bilingual experiment was rejected as described above. Five isolated Pixel tests passed (language profiles, recognition uncertainty, bilingual fixtures, automatic commands and tracked-speaker event changes). Final build and lint passed, with 196 core tests passing. The final APK was installed on the Pixel and its SHA-256 matched the local build. Another real microphone trial remains required. These routing changes do not establish reliable natural code-switching or owner attribution. They cannot recover missing audio from the previous trial, because audio was discarded.

## Phone retest

Auto-detect on. Keep the phone roughly 30–50 cm away in a quiet room and speak normally. Pause about two seconds between each numbered line.

1. 저는 내일 저녁에 파티에 갈 거예요. 파티에 참석할 예정이에요.
2. The party starts tomorrow at eight p.m. at The James.
3. 내일 있을 파티는 자정에 끝날 예정이에요.
4. Change tomorrow’s party to nine p.m. The party will be at Memorial Union instead of The James.

Wait ten seconds, then finish listening. Expected: one party tomorrow, 9 p.m. to midnight, at Memorial Union, using the phone timezone. Recognition and voice diagnostics must also be reviewed; absence of an automatically saved plan is a failure of the complete scenario, even if some words appear correctly.
