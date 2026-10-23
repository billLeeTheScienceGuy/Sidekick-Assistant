# Playback evaluation and speaker attribution

## Results on 2026-10-23

Five ActivityTimingTest instrumented checks passed on the physical Pixel 7a in
0.223 seconds, using the separate benchmark package and in-memory test storage.
Two new checks replay an original multi-turn party script and exercise the
informational-event setting. Three existing checks cover approved-item revisions,
venue correction/address clearing, and optional start/end persistence.

The replay checks one stable draft: tomorrow 8 pm at The James, end initially
unknown, then 11 pm, corrected to midnight, venue corrected to The Sylvee, and
an explicit address added. Changes to approved items remain review proposals.
These are text-to-proposal checks, not microphone or Whisper accuracy scores.

## Speaker playback

Rachel's English, Making Plans / English Conversation on the Prairie:
https://www.youtube.com/watch?v=fJnE8ktm4aQ
Creator transcript: https://rachelsenglish.com/making-plans-english-conversation-prairie/

Use the conversation at about 0:29–0:46 and the fuller replay at about 6:45–7:26.
The creator transcript supports comparison of arrival versus departure times and
tentative travel-duration estimates. It supplies no firm calendar date or street
address: a correct assistant must not invent either. Repeated lesson examples
must not be treated as separate real commitments. This is a challenging natural
speech/transcription test; the current rules may produce no event suggestion.

For a precise event-update test, speak these original lines, leaving 15 seconds
between each. Start a fresh session and keep it open afterward for inspection:

1. There's a party tomorrow at 8pm at The James.
2. It ends at 11pm.
3. Actually it ends at midnight.
4. Actually it's at The Sylvee.
5. It's at 25 S Livingston Street.

Expected: one informational suggestion, tomorrow 8 pm through the following
midnight, The Sylvee, with the literal stated address. Nothing saves automatically.
The address is a synthetic test input, not a verified venue lookup result.
Do not mix this script with the video in the same session when scoring it.
Compare time/place accuracy, missing or duplicate suggestions, correction handling,
and whether anything persisted without approval. No full microphone result has
been scored yet; source audio is not retained by the app.

## Agreed speaker-ID direction (implemented in 0.9.0)

Keep other speakers' events as informational suggestions; only a confident enrolled
user match may supply user-owned commitments. Uncertain/short/overlapping segments
remain unassigned. See SPEAKER_ID.md for the implementation and validation limits.

Implemented free WeSpeaker VoxCeleb ResNet34-LM embeddings through sherpa-onnx's Android
speaker-identification runtime. The model is CC-BY-4.0; retain attribution and pin
the exact artifact/hash before integration. Use several short enrollment samples,
store only local embeddings with a delete control, and clear raw enrollment audio.
Run embeddings on speech segments rather than silence. Measure false acceptance,
false rejection, overlap handling, latency and extra power on the Pixel before
choosing thresholds. Speaker matching is not authentication, consent detection,
or a reliable defense against replayed voices.

Sources:
- https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM
- https://k2-fsa.github.io/sherpa/onnx/speaker-identification/apk.html

## Consent and automatic activation

Use one session-start acknowledgement and visible ongoing controls, without per-line
prompts. Everyone included should agree to transcription as a product default;
new participants or withdrawal require the user to stop. Version 0.8.3 clarifies
the existing start dialog; there is no automatic participant/consent enforcement.
Future speaker ID must not turn consent into a persistent global permission for a
person, infer agreement from silence, or authorize capture after it already happened.

Do not implement a GPS-based legal yes/no switch: participant locations, call type,
expectations of privacy, jurisdiction and purpose matter. No saved audio is not a
general exemption from interception/eavesdropping rules. Location-specific guidance
would need a maintained legal source set, separate from venue lookup. No state-law
engine or legal-compliance certification is currently implemented.

Sources consulted:
- https://www.govinfo.gov/content/pkg/USCODE-2024-title18/html/USCODE-2024-title18-partI-chap119-sec2511.htm
- https://dwd.wisconsin.gov/dvr/policy-guidance/legal/recording-conversations.htm
- https://courts.ca.gov/system/files/2024-08/judicial_council_of_california_civil_jury_instructions_2024.pdf

An explicitly started microphone foreground service can gate Whisper automatically
while the screen is off. A stopped app cannot hear speech through a closed mic.
Background microphone start is restricted; default-assistant/hardware Sound Trigger
integration is separate and is not guaranteed for arbitrary voice detection.

- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://source.android.com/docs/core/audio/sound-trigger
