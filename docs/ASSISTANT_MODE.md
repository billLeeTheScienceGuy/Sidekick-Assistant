# Default assistant and recovery — 0.14.0

The user approved replacing Google's default assistant with SideKick. Android's
assistant framework provides a supported background microphone-start exemption;
this is not a root or permission bypass. Select SideKick using **Settings → Choose
default assistant**. The Pixel was configured through Android's confirmation UI.

## Behavior

- Choosing the assistant alone does not start the microphone.
- **Resume enabled listening after system restart** defaults on. Start live notes
  once and confirm the session notice to arm recovery. The notice explains that
  this remains armed across reboots until stopped. Everyone included must agree.
- System binding, boot/unlock/update recovery and sticky service restarts can
  resume an armed session while SideKick is still the selected assistant, the user
  is unlocked, and microphone/notification permissions remain granted.
- Finish, Stop and Discard disarm recovery synchronously before stopping. Turning
  recovery off disarms it without interrupting an already running manual session.
  Enrollment is never sticky or automatically resumed. Changing default assistants
  stops an armed SideKick session. Fatal capture errors disarm instead of looping.
- The assistant gesture opens a small panel with live listening status, Open
  SideKick and Stop listening. It is a local memory-assistant prototype, not a
  replacement for every Google/Gemini feature or a general voice-command agent.

The system-bound service stays lightweight and delegates capture to the existing
visible microphone foreground service. Speech detection, Whisper, attribution and
Hands-off saving remain unchanged. No screen contents or screenshots are requested:
the active service explicitly disables both context flags (`mDisabledShowContext=3`).

Android also requires a recognition-service declaration for assistant eligibility.
SideKick provides a bounded on-demand local dictation adapter using the existing
Whisper engine. It rejects competing capture while live notes are active. It ends
after silence or at fifteen seconds; this limit does **not** apply to continuous
live notes. It uses the app's selected speech language and writes no audio files.

## Limits

This is normal microphone capture with VAD, not Pixel DSP hotword detection, and
does not add an enrolled wake phrase or Google's “Hey Google” functionality.
Battery impact and all-day thermal behavior remain unmeasured. Recovery can have
gaps; in-flight RAM audio and unapproved text cannot survive process death. Saved
events and their Undo journal remain in Room. Android Force stop, permission
revocation, microphone privacy controls and OS failures are not overridden.

## Verification, 2026-10-23

Android 14 emulator, host microphone explicitly disabled:

- Two instrumentation checks passed in 0.792 seconds: service metadata/system-only
  binding, selection without arming, real background capture after an explicit
  start, persisted disarming and no restart after Stop.
- A separate recognition-adapter check passed in 5.635 seconds: competing capture
  returns BUSY and silent on-demand recognition times out cleanly.
- A host-orchestrated real process kill changed PID 15209 to 15367. The system
  rebound the assistant, microphone foreground service returned, and the assistant
  panel showed an active listening state.
- A full emulator reboot resumed the armed microphone service after unlock. Before
  opening the app, dumpsys showed its microphone foreground service; the app then
  displayed “Listening · waiting for speech” and “microphone stays on.” Finish cleared
  the persisted armed flag. The emulator's original Google assistant was restored.

Instrumentation completion itself force-stops the tested package, so process-kill
and reboot checks were performed outside the instrumentation lifecycle. Force-stop
is deliberately not treated as a successful-recovery scenario.

The physical Pixel 7a runs Android 17. Default role holder and active service binding
were verified as `com.sidekick.app`, with screen context disabled. Its microphone
was not opened automatically. A user-started Pixel restart/reboot trial is still
required before claiming physical-device recovery reliability. Both build variants
and lint pass. Earlier speech-quality benchmarks were not repeated.

## Platform references

- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [Microphone background-start exemptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions)
- [Sticky service restarts](https://developer.android.com/reference/android/app/Service#START_STICKY)
- [Assistant role qualification](https://source.android.com/docs/core/permissions/android-roles)
- [Force-stop behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)
