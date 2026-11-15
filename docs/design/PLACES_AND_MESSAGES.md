# Places, informational events and messages (0.8)

Settings makes all three choices switchable:

- Address lookup: automatic suggestions while reviewing, or manual lookup/Google Maps.
- Messages: sharing selected text, scanning incoming notifications, or both. Either can be disabled independently.
- Location context: a city you set, or the phone's approximate location with Android permission.

Speech and language models still run on the phone. There are no paid API keys.
The Android geocoder may contact its backend for a venue name and city or approximate
bounding box. It is a best-effort service, not a guaranteed Google Places search.
Maps links open Google Maps/the browser on a tap; they do not return a selected place
back into SideKick. The original venue wording remains until you change it. No location
is continuously tracked or stored. Address lookup runs during review, not for every
incoming notification. Fresh fixes from fused and network location providers are checked before requesting
a new fix. City is the fallback when phone location is unavailable. Internet access
can look up a venue within a city but does not itself guarantee a current location fix.

## Events and places

**Suggest events even if I'm not attending** is on by default. Factual event suggestions
are information-only, require approval, and do not trigger calendar-conflict checks.
Personal plans still appear when this setting is off. The review checkbox lets you
change an individual event between personal and information-only. Existing approved
commitments keep their earlier classification until you edit them.

Activity start, end, place name and address can all be unknown. Use the optional place
section to edit a name/address, clear it, or check it in Google Maps. Lookup results
are labeled possible addresses: tap **Use this address**, then **Save & accept** to
persist one. No lookup result silently changes a saved record. Geocoder failures,
ambiguous venues or missing city/location preserve the spoken name and unknown address.

Bounded conversation context can connect "party at 8pm at The James" with a later
"The James is at 432 W Gorham Street" or "It ends around midnight". Later details
refine a draft; if already approved in the retained session, they create an update
proposal requiring approval. Manual changes are retained unless explicitly replaced.
This is narrow English parsing with review, not general entity resolution. Context
cannot link arbitrary old conversations after restart or after its bounded window expires.

## Selected-message sharing

Enable **Accept messages I share into SideKick**. Share selected plain text from a
messaging app to SideKick, then confirm **Find activity suggestions**. You can also paste
text into Capture and choose **Analyze as message text**. The normal Find commitments
button is for your own words; incoming sender plans are never assumed to be your promises.
Shared-message analysis can use the selected conversation excerpt, including later end/
place statements in that excerpt. No SMS/history permissions are requested.

## Incoming notifications

Enable **Suggest activities from new message notifications**, tap **Manage Android
notification access**, and grant SideKick access in Android. Select the messaging apps
SideKick may read; without an app selection it stays inactive. Selecting an app does
not grant Android access automatically. Disable scanning or revoke access to stop.

Only newly posted message notifications from selected apps are processed. Group
summaries and non-message notifications are ignored. Notification history predating
enablement is excluded. Hidden/redacted notification text and old messages in your
inbox are unavailable. SMS, RCS and other messengers work only as far as their
notification payloads expose message text; user testing is needed for each selected app.
There is no sending, replying, inbox modification or message-history scraping.

Notification text is kept in bounded RAM: at most 20 conversation contexts, 6,000
characters each; inactive contexts are discarded on the next input after two hours. Each conversation has
separate context, and incoming plans become information-only review suggestions. Only
approved event snippets and details are persisted, never a full message inbox. Process
death clears unapproved suggestions. The queue is bounded and can drop older pending
notification updates during a burst. No message text or location is logged. Delete all
saved memory also clears retained message context.

## Phone checks still needed

1. Choose a city (for example Madison, Wisconsin) or phone location in Settings.
   Review a venue, check possible results, and confirm a correct address. Test manual
   lookup mode as well. Try an unknown venue and confirm its literal name remains.
2. Share a harmless selected conversation; confirm suggestions are temporary until approval.
3. Enable notification access and choose your messaging app. Have a test conversation
   mention an event, then a later ending/address. Check the draft or update proposal.
   Try an unselected app and turn scanning off to confirm no new suggestions appear.
4. Save an information-only event that overlaps your calendar: it should not ask for a
   conflict check. Change it to personal if you want the optional calendar check.

Automated checks use synthetic text/providers and never grant personal notification,
location or calendar access.

References: [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider),
[Geocoder](https://developer.android.com/reference/android/location/Geocoder),
[Maps URLs](https://developers.google.com/maps/documentation/urls/get-started),
[notification listener](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

## Setup and venue corrections (0.8.1)

Settings now shows location permission, Android Location and message-notification
readiness. It refreshes when returning from Android settings. **Test phone location**
checks the actual foreground path and shows fix age/approximate accuracy without
coordinates or saving the fix. This tests approximate location, not satellite navigation.

Changing a venue clears its old address, including a later spoken correction such as
"Actually it's at The Sylvee." If the activity was already approved, the change is a
review proposal. The stored venue/address remain unchanged until approval. An address
mentioned later can fill the corrected venue through another proposal. Manually changing
a venue name also clears the old address so it can be confirmed again.
