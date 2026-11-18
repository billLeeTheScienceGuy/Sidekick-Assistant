# Date understanding (0.24)

Integrated Yi Huang's English date work from commit 6f9cff4, preserving authorship.
The shared grammar handles month/day dates, explicit years, “day after tomorrow,”
“this Friday,” and relative day/week counts. It is used by activity detection,
reminder suggestions, rescheduling, venue trimming and event subject matching.

“Next Friday” remains ambiguous in ordinary speech. Activity suggestions retain
the following-week interpretation and flag it for review. Hands-off mode saves
that suggestion without an automatic reminder; a date chosen in the editor can
still have one. Automatic rescheduling does not choose between the interpretations.

“In 30 minutes” and “in an hour” use elapsed time from capture, including seconds.
They work across DST changes without shortening the requested delay. Absolute
wall-clock times in a DST gap or overlap stay unresolved. Midnight is supported.

Compound and fractional delays add up to one elapsed time: “in an hour and a half,”
“in 1 hour 30 minutes,” “in two and a half hours,” “in 1.5 hours” and “in forty-five
minutes.” Ranges such as “in an hour or two” or “in a minute or so” stay unresolved.
“This weekend,” “next week” and similar spans name no single day and stay unresolved.

Alternative clocks, alternative offsets, compound durations outside the supported
grammar, conflicting dates and invalid calendar dates stay unresolved. Multilingual
rules also reject alternative start clocks in Korean, Chinese, French, German and
Russian. Conflicting Arabic ending dates do not receive an invented date.

The date parser is a bounded grammar. It does not claim to understand arbitrary
relative dates, every dialect or every spoken numeral. Synthetic/emulator checks
verify parsing and app behavior; real speech recognition and battery use still
require physical-device trials.
