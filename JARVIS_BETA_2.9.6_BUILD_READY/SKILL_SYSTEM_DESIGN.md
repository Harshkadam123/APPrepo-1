# JARVIS Universal Hunter Skill System

The Hunter system is now a general progression framework for **fitness, academics, technical skills, communication and competitive achievements**.

## Universal rules

- Every skill has its own XP bar and level (0–10).
- XP is stored locally on the phone.
- A skill stops gaining XP at level 10.
- A mastered basic skill is hidden only when its promoted skill is unlocked.
- The next skill then becomes the active progression target.
- Prerequisites are explicit skill-level requirements.
- Activities should represent real, verifiable practice/work; the UI does not claim to verify a user's real-world result.
- S/SS/SSS/SSS+ are JARVIS game-style ranks, not professional certifications.

## Activity methods

Different skills use different measurable units:

- **Swimming:** every completed 100 m = one skill level, capped at level 10. The app does not round partial distance up.
- **Data Cleaning:** verified dataset-work units give XP. A small 15–30 row/record cleaning task can be logged as a genuine work unit.
- **Chess:** lessons, tactical puzzles, annotated games and serious games.
- **Physics:** verified problem sets, labs, simulations and project milestones.
- **English Communication:** language exercises, speaking sessions, professional communication tasks and recorded/verified speaking practice.
- **Programming:** coding problems, engineering tasks and project milestones.
- **Fitness:** verified working sets, movement sessions and endurance activities.

## Skill trees

### Data → AI

1. Data Cleaning F Lv 10
2. SQL F Lv 7 + Math F Lv 7
3. Unlock S ML Concepts
4. ML Lv 10 + Math Lv 9 + SQL Lv 8
5. Unlock SS Deep Learning
6. DL Lv 10 + Data Cleaning Lv 10 + SQL Lv 10 + Math Lv 10
7. Unlock SSS Real-World ML Projects
8. Real-World Projects Lv 10
9. Unlock SSS+ Competitive ML
10. SSS+ XP is reserved for genuine verified competition achievements.

### Chess

Chess Fundamentals F → Chess Tactics D → Chess Strategy A → Competitive Chess S → Chess Mastery SS.

### Physics

Physics Foundations F → Mechanics D → Advanced Physics S → Applied Physics Projects SS → Physics Research SSS.

### English Communication

English Foundations F → English Speaking D → Professional Communication A → Public Speaking S → Communication Mastery SS.

### Programming

Programming Fundamentals F → Software Engineering A → Production Projects SS → Competitive Engineering SSS.

## Design intent

The same engine can be extended to any subject by adding a `SkillDefinition` with:

- name
- category
- rank
- practice unit
- XP-per-level
- prerequisites
- promotion target

This keeps the progression system reusable instead of hard-coding every subject into the fitness UI.
