# JARVIS BETA 2.8 — Proactive Intelligence Changelog

## Added
- Proactive decision engine with deterministic priority scoring.
- Availability states: Busy, Free, Study, Class, Exercise, Meeting, Sleep, Travel and Custom.
- Persistent proactive event queue and lifecycle states.
- Smart interruption and quiet-period behavior.
- Morning briefing and evening review controls.
- Next-best-action engine.
- Deadline-aware priority escalation.
- Evolution integration for quests, skill progression and XP context.
- Study/task integration through the existing local task system.
- Authorized calendar signal integration without adding permissions.
- Snooze, dismiss, complete, reschedule and manual priority support.
- Feedback loop for repeated snoozes/dismissals/follows.
- Event deduplication across task/Evolution sources.
- Daily planning and deterministic task breakdown.
- Voice-command intent routes for proactive commands.
- Today / Command Center UI.
- Background local check using the existing alarm/notification capability.
- Unit tests for priority and planning primitives.

## Security / privacy
- No new dangerous Android permission.
- No automatic message/email sending.
- No private communication content access.
- No photo/file/banking/credential access.
- Irreversible or consequential actions remain behind existing confirmation/security gates.

## Compatibility
- Room migration 6 → 7.
- Version code 280.
- BETA 2.8 baseline. Version 2.8.0-production was superseded by BETA 2.8.1.
- Existing BETA 2.7 feature modules retained.
