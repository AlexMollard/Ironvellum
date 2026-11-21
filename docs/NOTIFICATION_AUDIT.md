# Notification audit — 2026-11-21

Scope: Android notification headings, body selection, plain/themed wording,
translations, lock-screen copy, channels and tap destinations.

| Notification | Result | Tap destination |
| --- | --- | --- |
| Social activity | Fixed the generic “A message” heading for all ten event variants; batches now count updates. Public copy stays generic. | Inbox |
| Workout in progress | Exercise heading and current set/rest/progress copy already match the workout state. Existing private version respects hidden lifts. | Active workout |
| Rest finished | “Rest is over” already describes the event. Fixed exercise/load details bypassing the hide-lifts lock-screen setting. | Active workout |
| Evening reminder | Routine heading, exercise/set counts and streak copy already describe the scheduled workout. | Today |
| Weekly recap | Week heading and summary already describe the recap; public copy hides figures. | Recap for that week; separate share action |

Single social headings cover friend requests, accepted requests, comments,
replies, reactions, group joins, group goals, departures, leadership changes
and removals. Each resolves in the current language and wording mode. English
and all seven shipped translations have the new headings and update plurals;
no locale retains the old notification message/missive wording.

The five notification IDs and tap request codes remain separate. Social
notifications still coalesce into one replaced row, retain their delivery
watermark, respect notification permission/channel settings, and open the
inbox. Existing backlog, duplicate, account-switch and retry behavior is
unchanged.

Regression coverage in `InboxDeliveryTest` checks actual Android tray titles,
bodies and expanded text for all ten event variants in both English wording
modes, public privacy, mixed batches, and accepted/public/batch titles in all
seven translated locales and both wording modes. `LiveWorkoutNotificationTest`
checks rest-finished privacy for every alert mode and both wording modes.
The rest builder used by the service is tested directly; timer expiry delivery
is not newly simulated. Translation checks cover keys, placeholders and each
locale's plural categories; they are not native-speaker review.

The audit uses source review and emulator tests with synthetic events, not
messages sent to real accounts. Android 16 notification promotion remains
controlled by the device and the user's system settings.

Validation: the full local FOSS gate passed in **9m31s** on 2026-11-21,
with 2,912 unit tests, 873 instrumented tests, 380 web tests and four asset
checks passing; lint reported no issues. This used warm Gradle caches and the
installed running API 35 emulator (`Ironvellum_Art_API35`, swangle, 8 cores /
8 GB), with the gate's emulator lock and ten-minute limit unchanged. All seven
translation checks reported zero errors; their length warnings remain advisory.
