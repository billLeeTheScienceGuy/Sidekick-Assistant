# Memory controls and local risk advisories

Audio is transient: capture buffers, queued segments and inference inputs are
wiped after use. SideKick does not save conversation recordings. Recent transcript
text and unapproved suggestions live in process memory. Saved events contain
their selected source evidence; deleting an event also deletes its Undo snapshots.
Android backup is disabled. Voice profiles and installed weights use private storage.

## Retention and export

Settings → Memory & privacy defaults to keeping saved events until deleted.
Optional 30/90/365-day cleanup requires an explicit confirmation because deletion
is irreversible. The latest captured/event/end/reminder/delivery timestamp must
be older than the cutoff. Future items, undated accepted items and accepted items
with undelivered reminders are retained. Cleanup runs at process startup and
daily while the process remains active; Android may stop an idle process.
Individual deletion and Delete all saved memory remain available.

Export saved events writes a versioned UTF-8 JSON document through Android's
file picker. It contains saved event fields and ISO instants with timezone IDs.
Saved source excerpts are excluded unless explicitly selected. Temporary full
transcripts, raw audio, voice signatures, source/sender identifiers and Undo history
are never exported by this feature. The chosen file destination may sync online.
An interrupted export can leave an incomplete destination; saved memory remains
unchanged. This is an export, not a restore/import or encrypted-backup feature.

## Risk advisories

The prototype inspects already-authorized live transcript text, manually analyzed
text and selected incoming/shared messages locally for a small set of English
warning signs: gift-card payment/code requests, private authentication-code or
password requests, remote-access requests, and supposedly safe-account transfers.
They are grounded in [FTC gift-card guidance](https://consumer.ftc.gov/articles/avoiding-and-reporting-gift-card-scams),
[FTC impersonation examples](https://www.ftc.gov/system/files/ftc_gov/pdf/imposter-scams-spotlight-2024.pdf)
and [FBI tech-support scam guidance](https://www.fbi.gov/how-we-can-help-you/common-frauds-and-scams/tech-support-scams).

Warnings identify patterns, not fraud or a guilty person. Legitimate requests may
trigger them; unfamiliar wording and other languages may be missed. Defensive
phrases such as “never share a verification code” are filtered. Ordinary gift
planning is not treated as gift-card payment pressure. This is a rule baseline,
not a trained or evaluated general scam detector.

Warnings appear in the app and, when permitted, a private notification. Each
category is rate-limited to once per 15 minutes per process. Only generic category
and advice are kept; no credentials, source text or phone numbers are copied into
the warning. Dismiss removes it; the setting disables inspection and clears
notifications. Nothing blocks actions, sends money, contacts anyone or uploads
conversation text. No warning does not mean a request is safe.

Message setup distinguishes notification-access approval from a live Android
listener connection. SideKick requests reconnection when scanning is enabled or
the app resumes, and after a listener disconnect. It does not bypass Android
access revocation. The callback lifecycle follows the
[Android listener API](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

## Consent limits

The start flow requires agreement from everyone included. Stop if agreement is
withdrawn or new participants have not agreed. Voice matching does not establish
consent. Settings → Listening privacy & region guidance provides limited contextual
summaries without making a legal eligibility decision. An explicit city/state
works offline. A bare city or ambiguous “IL” is not assigned a jurisdiction.

Optional phone-region refresh is off by default and manually triggered with
approximate-location permission. Android's location/address provider may send
coordinates online. Only country/admin area and the refresh time are cached
locally for a 24-hour display lifetime; no coordinates, street or history are
stored. Turning this off clears the cache. There is no background location
permission, continuous tracking, automatic legal permission or per-person
consent database. Borders, remote participants and setting-specific rules
cannot be resolved by a phone location.

The limited catalog links [Wisconsin DWD guidance](https://dwd.wisconsin.gov/dvr/policy-guidance/legal/recording-conversations.htm)
(reviewed May 2014, explicitly identified as dated),
[Illinois law](https://ilga.gov/documents/legislation/ilcs/documents/072000050K14-2.htm)
and the [federal statute](https://uscode.house.gov/us/usc/119-118/t18/s2511/).
Sources were checked November 8, 2026. Participant-consent exceptions, private
conversation rules and other restrictions differ; these summaries never relax
SideKick's everyone-agrees start policy. Other regions are explicitly not covered.
This is educational guidance, not a compliance engine or legal authorization.

Anonymous speaker keys and bounded centroids are session-only, not named-person
profiles. The last twelve optional speaker excerpts remain temporary. Independent
owner matching cannot prove consent. See SPEAKER_TURNS.md.

Optional calendar mirroring can send titles, times and places through the chosen
calendar account. It requires separate confirmation and never copies source
excerpts, transcripts or profiles. Disabling pauses writes and leaves copies;
revoked permission can leave ID/hash cleanup records until access returns.
See CALENDAR_SYNC.md for deletion and external-edit protection.
