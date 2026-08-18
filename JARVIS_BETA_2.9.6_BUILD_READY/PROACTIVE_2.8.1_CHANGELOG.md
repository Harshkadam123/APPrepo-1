# JARVIS BETA 2.8.1 — Proactive Intelligence Hardening

Incremental update from the existing BETA 2.8 codebase. No unrelated feature set and no new Android dangerous permission.

## Added / strengthened
- Time-aware deterministic daily planner using task duration, deadlines, goal priority, consequence of delay, protected availability windows and authorized calendar blocks.
- Next-best-action now considers available time, task duration, completion percentage and Evolution weak-skill context.
- Task metadata for estimated duration, completion percentage, consequence and goal priority, migrated safely from database 7 to 8 with defaults preserving existing tasks.
- Protected calendar events participate in interruption suppression.
- Critical alerts only bypass protected periods when the user's explicit critical override is configured.
- Morning briefing now includes due-today workload and upcoming authorized schedule context.
- Evening review includes completion state, XP, tomorrow deadlines and weakest tracked skill.
- Positive feedback slightly increases future relevance; dismiss/snooze feedback reduces interruption pressure.
- Added planner tests for deadline/goal selection and protected-window behavior.

## Preserved
All BETA 2.7/BETA 2.8 privacy, accessibility, AI, voice, PDF, communication confirmation, Evolution, offline/local-first and permission behavior remains incremental and backward-compatible.
