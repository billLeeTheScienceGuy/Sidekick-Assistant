# Continuous listening and speech activation

SideKick already detects speech automatically **after you start Live notes**.
The microphone remains active in a foreground service. Silero VAD decides when
bounded audio should reach Whisper. Silence does not intentionally invoke Whisper.
This is speech-gated transcription, not a microphone that powers off and wakes on sound.
Audio is held in bounded RAM and discarded, never recorded into an audio file.

Start from the open app, grant microphone/notification permissions and confirm the
listening dialog. The foreground notification provides Finish and Discard controls.
The session has no five- or fifteen-minute timer. Five-second inference chunks do not
limit the conversation length. Screen-off operation uses a renewable partial wake lock.
Finish drains pending speech; Discard cancels and clears this session's temporary notes.
The new status line distinguishes Speech detected from Waiting for speech (mic still on).

An ordinary microphone foreground service cannot generally start itself from the
background or after boot because RECORD_AUDIO is a while-in-use permission. This
prototype therefore requires a user start and does not silently resume after process
death or reboot. The microphone must be running to detect voices. Hardware wake-word
capabilities are a separate Android/device integration, not what this VAD provides.

## Automated checks

ContinuousListeningTest requires `-e verifyContinuousListening true` to run. Its
physical-device case injects public JFK audio into the real Silero/Whisper pipeline;
it does not open the physical microphone. It alternates speech with long digitally
silent pauses and counts actual Whisper invocations. Its emulator case opens the real
Android AudioRecord/foreground-service path with host microphone input disabled,
turns the screen off, and checks liveness, silence gating, Finish and bounded memory
statistics over six minutes. The long tests are skipped by ordinary test runs.

These measure different layers. Digital silence is not realistic room noise, and
public test audio does not establish transcription accuracy for your conversations.
Emulator CPU/memory are not Pixel power measurements. USB-powered tests cannot prove
unplugged battery life. Neither test establishes 24-hour or process-death recovery.

## Real microphone trial

After automated checks, use your own speech for a 20–30 minute session with several
quiet periods. Confirm that text resumes without restarting, the screen can stay off,
and Finish drains the last sentence. Note missed/duplicated words, silence producing
text, interruptions and how quickly text appears. Speech detection does not identify
who spoke; voice enrollment remains a separate feature.

For power measurements, compare an unplugged idle baseline with an equal-length
listening session at similar starting battery, screen state and temperature. Separate
quiet-room listening from frequent speech; note thermal changes and peak memory.
Longer overnight validation comes after those comparisons.

Reference: https://developer.android.com/develop/background-work/services/fgs/service-types#microphone

## User microphone trial (2026-10-23)

User reports a roughly 20-minute speaker-playback trial, successful transcription
with the screen off, and a displayed battery level remaining at 100%. Inspection
afterward confirmed recent transcript text in the normal Pixel app and no active
listening service after Finish. The text contains coherent dialogue and a trip
itinerary, alongside recognition errors. Source audio was not retained, so word
accuracy and coverage cannot be independently scored. No recent SideKick crash
appeared in the inspected log buffer.

This is a functional screen-off result, not a measured battery-life result. There
was no start/end charge-counter capture or idle baseline; the phone reported
charging when inspected afterward. A displayed 100% throughout cannot establish
zero energy use or predict all-day runtime. Follow-up power comparison should
start below full charge, unplugged, with matched screen state and idle baseline.

## Pixel pipeline measurement (2026-10-23)

The physical Pixel 7a processed three real-time paced public speech bursts separated
by long digital pauses. Whisper invocation counts were 4, 8 and 12 after each burst;
each following 15-second silent interval added zero calls. The test passed in 118.623
seconds, with 118,513 ms measured pipeline elapsed time and 47,605 ms process CPU time.
Those CPU figures include speech inference and test/framework work, not silence-only
power. They do not estimate battery drain. The physical microphone was not opened.

## Emulator foreground-service measurement (2026-10-23)

The screen-off AudioRecord session passed for six uninterrupted minutes, including
the five-minute wake-lock renewal branch and clean Finish. Each minute reported
0 transcription chunks. Measured elapsed time was 360,017 ms, process CPU time
40,416 ms, and total PSS was 110,246 KB initially / 103,646 KB at the end. This is
an Android 14 x86_64 emulator, not Pixel battery or silence CPU evidence. The whole
test took 367.416 seconds. Host microphone input was explicitly disabled before it ran.
Both timed tests passed; ordinary debug and benchmark builds/lint passed. Version
0.8.2 adds speech/waiting status and the Whisper invocation counter used by the test.
