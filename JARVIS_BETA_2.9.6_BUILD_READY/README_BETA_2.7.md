# JARVIS BETA 2.7 — Production-Oriented Focus Edition

JARVIS 2.7 combines the existing private AI assistant core with a Regain-class study/productivity layer. The focus system is local-first: focus history, tags, notes, goals, streaks, block lists and planner data stay on the device unless a future user-enabled sync layer is explicitly added.

## Feature matrix

| Area | BETA 2.7 |
|---|---|
| Focus timer | Yes |
| Stopwatch | Yes |
| Pomodoro | Yes, custom focus/short/long breaks and cycles |
| Strict mode | Yes, no JARVIS-side early exit/pause |
| Session tags | Yes |
| Session notes | Yes |
| Focus sounds | White/brown/rain-like local generator |
| App blocker | Yes, AccessibilityService |
| Daily app limits | Yes |
| Reels/Shorts/Spotlight blocking | Yes, visible-UI based |
| Adult-site protection | Yes, inherited local blocker; no JARVIS-side disable control |
| Browser study mode | Yes, allow/block domain lists |
| YouTube study mode | Yes, local visible-UI guard + allowed-channel list |
| Screen-time | Yes, Android Usage Access |
| Daily/weekly/monthly usage | Yes |
| Focus goals | Yes |
| Streaks | Yes |
| Achievements | Yes |
| Planner | Yes, recurring weekly blocks |
| Local coach | Yes |
| Focus rooms | Yes, offline local rooms |
| Global live rooms | Backend required |
| Global leaderboard | Backend required |

## Production hardening already preserved

- AI does not directly receive unrestricted Android data.
- Permissions are checked by the existing JARVIS security layer.
- Blocking decisions are local.
- The adult blocker intentionally has no in-app disable/pause/stop command.
- Accessibility data used for focus controls is not sent to a remote service by this project.
- Strict mode is an app-level discipline feature, not a device-security guarantee.
- Screen-time data requires Android Usage Access.
- Exact focus completion notifications use AlarmManager when Android permits exact alarms and gracefully fall back when it does not.

## Build

Open the project in Android Studio and build the `app` module. This environment does not contain an Android SDK/Gradle runtime, so APK compilation must be performed on an Android development machine.


## JARVIS Evolution System

BETA 2.7 adds a local-only Evolution subsystem. It tracks configurable physical and learning skills, XP, levels, quests, long-term goals, achievements, streaks, progression history and recommended challenges.

Core progression is deterministic and does not require a large AI model. The subsystem is isolated behind `EvolutionRepository` / `EvolutionService`, so it can interact with JARVIS AI, tasks, proactive logic, schedule and voice layers without owning those systems.

No Android permissions were added for Evolution. It does not inspect unrelated personal data.
