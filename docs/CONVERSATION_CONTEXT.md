# Conversation context (0.23)

The local parser now assembles short exchanges such as:

> There's a party.
> Tomorrow.
> At 8pm.
> It's at The James.
> It ends at midnight.

Each field retains the original wording as evidence. A date supplied after a
clock replaces the inferred date. Date/time fragments use the existing English,
Korean, Chinese, French, German, Russian and Arabic grammars. Unknown or ambiguous
values remain unknown; this does not add general language-model comprehension.

An untimed personal plan becomes the same saved event when its time arrives,
including its reminder. In Review mode the draft also keeps its ID. Automatic
changes remain reversible with Undo; manual edits invalidate automatic ownership.

For a single event, English “I'll be there” and “I won't be there” update personal
attendance. Withdrawal keeps the event as information and removes its reminder;
it does not cancel the event for everyone. Multiple candidate events, another
speaker, incoming messages and reported speech cannot grant personal attendance.
Multilingual pronoun attendance remains future work.

Explicit English named end/venue details can refer back across another event.
Generic references shared by multiple events of the same kind are not applied.
This is literal subject matching, not fuzzy entity resolution.

Voice inference context resets on speaker changes, a gap longer than two minutes
or a local date change. Listening itself continues; only reference context resets.
Incoming notification messages retain separate conversation contexts and explicit
message boundaries, with context reset across local dates. Message notification
access is still opt-in; messages do not imply the phone owner's attendance.

Synthetic tests exercise these paths without activating the microphone or reading
real messages. They do not establish speech recognition accuracy, speaker matching
accuracy or sustained screen-off battery use. The long listening trial is deferred.
