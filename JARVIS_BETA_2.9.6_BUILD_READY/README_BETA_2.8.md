# JARVIS BETA 2.8.1 — Proactive Intelligence & Daily Life Orchestrator

BETA 2.8.1 is an incremental update from the existing BETA 2.7 codebase. Existing task, voice, privacy, accessibility, focus, Evolution, action-verification and local AI behavior remains intact.

## New system

The **Proactive Intelligence & Daily Life Orchestrator** adds a local-first decision layer for:

- tasks and deadlines
- Evolution quests, skills and XP
- user availability and protected periods
- optional daily briefing and evening review
- next-best-action recommendations
- persistent proactive event queue
- snooze, dismiss, complete, reschedule and priority controls
- feedback-aware interruption ranking
- deterministic notification deduplication
- optional authorized calendar signals
- task breakdown into local subtasks
- operation without an LLM

The LLM is not required for simple prioritization, deadline calculations, queue management, availability rules, deduplication or basic recommendations.

## Privacy

No new Android dangerous permission is introduced by BETA 2.8.1. Calendar signals are used only when the existing Calendar capability is explicitly authorized through the existing privacy gate and Android permission. Communication content, photos, files, banking data, credentials and private conversations are not accessed by the proactive engine.

## Interruption policy

- LOW: stored silently
- MEDIUM: normal notification
- HIGH: notification/voice only when availability permits
- CRITICAL: follows configured priority-alert behavior

Busy/protected periods queue non-critical events for later re-evaluation instead of dumping them immediately.

## Persistence

Room database migration 6 → 7 adds proactive availability, event queue, feedback and schedule tables. Existing data is preserved through the migration chain.

## Commands

- What's important today?
- What should I do now?
- Plan my day.
- What am I missing?
- What deadlines are coming?
- What did I not finish yesterday?
- Remind me when I'm free.
- Don't disturb me for two hours.
- Snooze this.
- Mark it complete.
- What's my next quest?
- How should I use my free time?
- Break down my ML project.
- Evening review.

## Android constraints

BETA 2.8.1 does not bypass Android restrictions. Background checks use Android alarm facilities already compatible with the project's existing permission model. Notification delivery remains subject to Android notification settings and system restrictions.


## BETA 2.8.1 hardening

The 2.8.1 update adds deterministic time-aware planning, task effort/completion/consequence metadata with a safe Room 7→8 migration, protected calendar-aware interruption handling, configurable critical override enforcement, richer daily briefing/evening review, and planner regression tests.
