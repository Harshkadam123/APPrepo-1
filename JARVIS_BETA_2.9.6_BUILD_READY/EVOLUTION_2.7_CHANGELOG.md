# JARVIS BETA 2.7 — Evolution System

## Major addition
JARVIS Evolution is a local, independent progression subsystem for skills, quests, goals, XP, levels, streaks, achievements, history and adaptive challenge recommendations.

### Privacy
- Evolution data is stored in the existing local Room database.
- No new Android permissions are introduced.
- The subsystem does not read photos, files, messages, banking data, unselected apps, or unrelated personal data.
- Core progression is deterministic and does not require an LLM.

### Compatibility
- Existing Room data is preserved with a 5→6 migration.
- Existing AI, voice, memory, task, privacy, confirmation, selected-app and focus systems remain in place.
- Large AI models remain outside the Evolution persistence/calculation path.
