# Prototype demo checklist

Use synthetic text and events for a reproducible demonstration. Stop live notes
first. The speech/battery field trials are separate and can be done later.

1. Show local operation and explain optional online venue lookup. Never claim
   all-day battery reliability or perfect speaker matching.
2. In Review mode, enter a personal plan for tomorrow with an explicit time.
   Show its evidence and date/time pickers, save it and show Planner.
3. Show the Summary tab on that date. Personal and information-only events are
   separated; unknown dates/end times are displayed honestly.
4. In Hands-off mode, use two named events, then an explicit attendance update
   for one. Verify only that event becomes personal. Withdraw attendance, rejoin
   and demonstrate Undo. Synthetic instrumentation covers these transitions.
5. Demonstrate a time correction and cancellation without duplicate events.
6. Open a known event in the calendar editor and review its fields. Cancel the
   calendar editor unless intentionally saving a demo event. This is a manual
   handoff, not calendar synchronization.
7. Show temporary conversation highlights and clear them. Saved events remain.
8. Open Settings → Memory & privacy. Show that automatic cleanup is off by default,
   a finite retention period asks before destructive cleanup, and export excludes
   saved source excerpts by default. Cancel the file picker unless creating a demo
   export. Temporary transcript/audio/voice profiles are never included.
9. Analyze synthetic message text: “Pay the overdue tax using gift cards.” Show
   the generic risk advisory, dismiss it, and show its settings switch. Do not
   type real credentials. Explain English-only rules, false positives and misses.
10. Explain that live intent extraction is bounded grammar, optional Qwen supports
    manual analysis, full diarization/general scam classification/legal-location
    automation are unfinished, and summaries are grounded/extractive.

Record failures alongside successes. Automated core/emulator tests establish
software behavior; they are not measurements of natural speech, battery or
user-study outcomes. Use VALIDATION.md for the exact tested scope.
