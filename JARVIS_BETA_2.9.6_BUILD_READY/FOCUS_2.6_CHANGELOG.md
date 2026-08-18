# JARVIS BETA 2.6 — Regain-class Focus System

This build expands the 2.5.1 foundation into a production-oriented, local-first productivity system inspired by the feature set currently advertised by Regain.

## Included

- Focus countdown timer and stopwatch.
- Custom Pomodoro focus, short break, long break and cycle counts.
- Presets for Study, Deep Work and Exam Strict mode.
- Strict focus sessions with no in-app early exit or pause path.
- Persistent session state and exact-alarm completion notification when Android permits it.
- Subject/session tags and notes.
- Local focus history.
- Daily focus goal.
- 7-day and 30-day screen-time views through Android Usage Access.
- Focus streaks and achievements.
- Local productivity coach using only local focus history.
- App blocking during focus sessions through AccessibilityService.
- Daily app limits.
- Reels / Shorts / Spotlight UI blocking during active use.
- YouTube Study Mode foundation using local visible-UI checks and an allowed-channel list.
- Browser study allow-list and local blocked-domain list.
- Always-on local adult-site protection inherited from 2.5.1; there is intentionally no JARVIS-side disable/pause feature.
- Weekly recurring focus planner with delete support.
- Local focus-room mode and local leaderboard.
- Offline focus sound generator with white, brown and rain-like noise support.
- Existing JARVIS privacy, permission, confirmation, action, verification and history architecture is preserved.

## Deliberate boundary

The only Regain-style capability that cannot honestly be called fully live/production without external infrastructure is worldwide real-time multiplayer rooms and global leaderboards. BETA 2.6 keeps this feature local/offline and isolates the future network layer so private focus history is not silently uploaded. A real deployment needs an authenticated backend, rate limiting, abuse controls, encrypted transport, account/session management and a published privacy policy.

## Android limitations

AccessibilityService can be disabled by Android system settings and does not provide a security guarantee against root, factory reset, OS replacement or other device-level control. JARVIS does not claim otherwise.


## 2.6.1 parser reliability patch
- Reworked reminder/time parsing to preserve task titles while removing only the exact schedule span.
- Added support for `about`, relative durations, half an hour, `from now`, compact times such as `7pm`, and weekday/date + clock combinations.
- Improved entity extraction so reminder titles no longer depend on the word `to`.
- Added regression tests for common reminder commands.
