# Arabic support

Version 0.21 adds Arabic (`ar`) to speech settings, local Omnilingual recognition,
automatic event extraction and explicit corrections. Arabic voice setup has its
own on-device signature and reading prompt. No network access is added to the app.
The UI remains English; Arabic source text is retained rather than translated.
Use Adaptive mode: Whisper tiny/base were substantially worse in this evaluation.
The bundled Apache-2.0 Omnilingual int8 model adds about 365 MB of model data.
Its observed process PSS reached about 844 MiB; this is not peak memory or battery
validation. Recognition remains imperfect: the recorded Arabic command comparison
recognized only five of eight command meanings, despite stronger public-speech
accuracy. Unclear corrections stay unapplied rather than inventing their targets.

The initial grammar targets Modern Standard Arabic. Examples:

- سأحضر الاجتماع غدا الساعة السادسة مساء.
- انقل الاجتماع إلى غدا الساعة السابعة مساء.
- ألغي الاجتماع.
- لا تلغي الاجتماع. (must not cancel)
- سأحضر حفلة غدا الساعة ٨ مساءً في قاعة النور.
- تنتهي عند منتصف الليل.
- تستمر لمدة ساعتين.

Western, Arabic-Indic and Persian digits are supported. Diacritics and alef variants
are normalized only for parsing; evidence keeps its original text. Today, tomorrow,
weekdays and ISO dates are supported. A bare ambiguous hour stays unknown, as do
conflicting dates, contradictory meridiem and alternatives. Matching uses literal
named subjects or generic event types, with the existing source/ownership limits.
Other speakers remain informational. Places are literal, with the existing optional
lookup workflow; address resolution is not guaranteed.

Opt-in test audio comes from public Google FLEURS (`ar_eg`, CC BY 4.0), and synthetic
standard-Arabic phrases read by two different voices. These are limited fixtures,
not broad validation of Egyptian, Gulf, Levantine, Maghrebi or other dialects.
The user does not speak Arabic; real native-speaker validation remains needed.

Prepare fixtures with `scripts/prepare_asr_commands.py` and
`scripts/prepare_language_fixtures.py`. Host-only synthesis uses Edge TTS; app
recognition stays offline and no user microphone audio is sent for synthesis.
Run isolated `MultilingualQualityTest` with `compareLanguages=true` to compare
Whisper tiny/base across all seven languages and SenseVoice settings for Korean
and Chinese. See SPEECH_QUALITY.md for results and routing decisions.
