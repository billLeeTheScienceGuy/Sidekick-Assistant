# Sidekick-Assistant

SideKick is a mobile, privacy-oriented memory assistant designed to help users remember and act on spoken commitments with minimal manual effort.

## Motivation and Target Users

People often make verbal commitments (for example, “I’ll call you at 5 PM”) and later forget to follow through. SideKick reduces this cognitive load by:

- passively listening in the background,
- extracting commitments and actionable details from conversations,
- checking those items against user context (such as calendar events), and
- suggesting or performing reminders/actions based on user settings.

Primary target users include:

- busy professionals managing many commitments,
- older adults who benefit from memory and scheduling support.

## Related Work and Key Differentiation

Existing tools provide isolated pieces of this workflow:

- transcription apps (for example, Otter.ai) convert audio to text but typically require manual recording/review,
- voice assistants (for example, Siri) can create reminders/events but require direct user interaction,
- voice-authentication tools identify speakers but do not provide end-to-end conversational action extraction.

SideKick combines these capabilities into a low-friction, background workflow:

- user-aware voice-triggered processing,
- diarization and local transcription,
- commitment/intent extraction,
- context-aware reminder and scheduling actions,
- conversation and daily summaries,
- privacy-first local processing with minimized retained raw audio.

## Project Introduction and Technical Challenges

### Core behavior

1. During setup, SideKick establishes a user voice profile and gathers permissions/consent.
2. In background mode, it maintains a short rolling buffer (1–2 seconds) instead of full continuous storage.
3. When user voice is detected, relevant audio is passed through speaker identification/diarization.
4. Consenting speaker segments are transcribed locally.
5. The transcript is analyzed for actionable entities and intents.
6. Actions are checked against existing context (for example, calendar conflicts).
7. SideKick generates reminders/actions/summaries according to settings and confidence thresholds.

### Main technical challenges

- **Privacy:** keep sensitive data local, minimize raw audio retention, enforce consent-aware processing.
- **Battery efficiency:** use lightweight gating (rolling buffer + voice detection) before expensive models.
- **On-device feasibility:** select algorithms based on accuracy, computational cost, and local suitability.

## Implementation Plan and Module Interfaces

The architecture is composed of seven modules:

1. **Calibration and Privacy Module**  
   Establishes user voice profile, handles permission/consent, manages retention/listening settings, and applies rough-location privacy-law checks.
2. **Background Audio and Voice Detection**  
   Maintains rolling buffer and triggers expanded recording only when user voice is detected.
3. **Recording and Speaker Diarization**  
   Segments activated audio and labels speakers.
4. **Local Speech-to-Text**  
   Uses consent permissions to transcribe only allowed speaker segments and delete disallowed audio.
5. **Intent and Entity Extraction**  
   Extracts commitments, dates, times, people, locations, and action types with confidence scoring.
6. **Context and Action Engine**  
   Compares extracted actions with user context, flags conflicts, and either auto-executes or requests confirmation.
7. **Memory and Summary Module**  
   Stores approved transcripts/commitments/summaries per retention settings and provides conversation/daily summaries and deletion controls.

## Primary Control Flow and Decision Points

Primary flow:

- voice detection → recording/diarization → transcription → intent extraction → context checks → action/confirmation → memory storage.

Decision logic:

- if user voice is not detected: skip full processing,
- if transcription/intent confidence is low: suggest/notify rather than auto-execute,
- if event conflicts with schedule/context: issue warning,
- otherwise: execute action according to user preferences.

## Milestones, Timeline, and Risk Response

The remaining ten-week plan includes overlapping ownership and integration support across team members.

- **Nicholas Beier:** audio, voice detection, privacy pipeline.
- **Emma Huang:** speech processing, intent extraction, action engine.
- **Bill Lee:** memory module, UI/integration, algorithm evaluation.
- **All members:** integration, testing, user study, documentation.

Key milestone targets:

- **October 15:** architecture and initial voice/privacy setup,
- **November 15:** end-to-end working prototype.

Primary risks and mitigations:

- unreliable voice identification,
- battery overhead,
- local model constraints,
- privacy/legal compliance complexity.

Mitigation strategy:

- early interface stabilization,
- incremental integration/testing,
- overlapping module knowledge to avoid single-person bottlenecks,
- fallback to simpler implementations while preserving core pipeline behavior.

## Candidate Algorithm Areas

- voice activity detection,
- speaker/voice matching,
- speaker diarization,
- speech-to-text,
- entity/intent extraction,
- time normalization,
- calendar conflict detection,
- summarization.

These will be evaluated on accuracy, runtime cost, privacy, and device-local execution suitability.
