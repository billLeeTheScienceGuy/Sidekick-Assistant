# Interface redesign — November 13, 2026

This is a desk review of official product documentation and design guidance,
not a timed competitor usability study. No paid account, cloud processing or
competitor code is part of SideKick. iPhone work was excluded at the user's request.

| Reference | Observed pattern | Applied to SideKick |
|---|---|---|
| [Notta mobile home](https://support.notta.ai/hc/en-us/articles/17604467003803-Introduction-about-Notta-App) | Home/search, direct capture/import actions, separate settings | Clear listening action; import/model details moved behind explicit controls |
| [Otter conversation layout](https://help.otter.ai/hc/en-us/articles/21695002816535-Learn-about-the-new-Conversation-page-layout) | Separate Summary and Transcript views | Stable Capture / Planner / Summary destinations; source text stays separate from saved facts |
| [Google Recorder](https://support.google.com/pixelphone/answer/16267698) | Distinct transcript editing and summary workflows | Optional note editing, concise recent suggestions and access to full Planner |
| [Android adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation) | Bottom navigation for compact windows, rail for larger windows | Bottom bar below 600 dp usable width; side rail at or above it |
| [Android adaptive layout guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout) | Adapt to the current window, including resizing | Bounded reading width, system insets, keyboard handling, wrapping controls and scrollable settings |
| [Samsung display controls](https://www.samsung.com/us/support/answer/ANS10001572/) | Separate font, display-size and appearance settings | Independent in-app text and interface sizing; Light/Dark/System |
| [Samsung Easy Mode](https://www.samsung.com/ae/support/mobile-devices/using-easy-mode-on-my-samsung-phone/) | A simpler presentation with larger targets and spacing | Simple home for daily use, Detailed home for notes and technical controls |
| [Todoist board layout](https://www.todoist.com/help/todoist/features/use-the-board-layout-in-todoist-AiAVsyEI) | Alternative views of the same tasks | Both home layouts use the same saved plans and settings |

Otter's referenced page describes its documented conversation layout and notes
that mobile changes are still rolling out. It informs the information hierarchy;
it is not evidence of identical mobile screens or measured usability superiority.
Samsung Easy Mode is a system-level reference, not a competing note-taking app.
SideKick's controls are app-local and never change Android's display preferences.

## Design decisions

- One primary listening action, explicit microphone state and persistent navigation.
- Neutral surfaces, restrained teal, consistent rounded cards and standard Android typography.
- Light, Dark and System appearance choices, saved on the device; text follows
  Android font settings with optional 90/100/115/130% text adjustment. Interface
  controls and spacing independently use 100/110/125%; touch targets never shrink
  below the phone-default size. Android font conversion is delegated to the
  original density, retaining nonlinear scaling on supported versions.
- Calm (teal/rounded), Classic (blue/crisper) and Warm (amber/serif headings)
  design presets work with either Light or Dark appearance.
- Simple home keeps listening, a next-plan preview and a detailed-view action
  together. On short windows or very large text it prioritizes the listening
  controls. Detailed home includes notes, transcript previews and expandable tools.
- Manual note-taking can be switched off independently; review/edit remains
  available and hiding the panel does not delete an existing temporary draft.
- Capture previews three recent suggestions; Planner retains the full history.
- Session diagnostics, audio import and optional model administration are disclosed
  on demand. Consent, stopped/error states and uncertain ownership remain visible.
- Settings use a wider scrollable panel with a fixed Close action, rather than a
  narrow dialog containing the whole form. Each destination retains its scroll state.
- Source evidence in list cards is a short preview. The editor retains full evidence.

## Compatibility scope

The interface adapts to available space; it does not check for a Pixel brand/model.
The existing native build supports Android 8/API 26 or newer on ARM64 phones and
x86-64 emulator devices. This release does not add a 32-bit native build or iOS.
Microphone access is optional for typed/Planner/Summary workflows. Device RAM,
microphone hardware, Android background policy and optional model sizes still
limit speech capabilities. Responsive layout is not a promise that every offline
model runs smoothly on every low-memory phone.

## Verification

`ResponsiveInterfaceTest` checks persistent navigation, draft preservation,
appearance persistence, optional details, actual window-dependent navigation and
48 dp primary targets without starting the microphone. The reproducible
`scripts/test_interface_layouts.ps1` matrix uses an isolated emulator package,
changes actual display size/density/font scale and restores those settings.
Screenshots contain only synthetic/empty test-app content, not personal data.
See VALIDATION.md for measured outcomes and the exact matrix.
