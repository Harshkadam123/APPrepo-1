# JARVIS 2.9.1 — Stability & Privacy Hardening

## Fixed

- Unified contact, calendar, dialer and messaging actions with `ActionExecutor` confirmation/policy gating.
- Contact/calendar access now defaults to `ASK`; background proactive workers only consume calendar data when explicitly set to `ALLOW`.
- Microphone use now checks the JARVIS Privacy Firewall before Android microphone access and records a local privacy audit event.
- Android personal-data permission prompts are no longer requested when the corresponding JARVIS privacy capability is blocked.
- Replaced heavy proactive work inside `BroadcastReceiver` with WorkManager `CoroutineWorker` execution and retry handling.
- Added durable proactive scheduling with a 15-minute WorkManager refresh plus daily 08:00/20:00 AlarmManager anchors.
- Added boot and app-replacement recovery for proactive scheduling.
- Daily proactive alarms use exact scheduling when Android allows it and fall back safely when exact alarms are unavailable.
- Offline map archive imports now enforce an 8 GB hard limit, use temporary files, reject empty imports, and avoid leaving partial files behind.
- Route-graph imports now enforce a 64 MB limit and validate JSON before replacing the active graph.
- Room schema export is enabled for migration review.
- Android backup of JARVIS private data is disabled to avoid silently copying memories, history, privacy audits and tasks to cloud/device-transfer backup.
- Accessibility-service documentation now accurately states that content-protection paths may inspect the active accessibility node tree, without persisting extracted text or keystrokes.

## Version

- `versionCode`: 291
- `versionName`: `2.9.1-stability`
