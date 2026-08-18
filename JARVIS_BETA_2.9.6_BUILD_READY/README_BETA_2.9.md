# JARVIS BETA 2.9.1.1 — AI / MODEL LAYER

This release is an incremental AI/model-only update from JARVIS BETA 2.8.1.

It does not add new user-facing capabilities, permissions, accessibility access, privacy scope, autonomous actions, or UI systems.

## AI changes

- Centralized model inventory and configuration.
- Deterministic capability-based model routing.
- On-demand model session abstraction for RAM-efficient future model loading.
- Memory-pressure policy for heavy inference avoidance.
- Graceful fallback to smaller/deterministic paths.
- Better paraphrase handling for existing commands.
- Existing tool systems remain authoritative for factual state.
- Short-lived conversational topic context remains in memory only.
- Internal benchmark data structures for accuracy/success, latency, load/unload and RAM observations.

## Current shipped model

The BETA 2.8.1 codebase contains one actual AI asset: `app/src/main/assets/intent_model.json`, a small JSON Naive-Bayes intent model. No generative LLM binary was present, so BETA 2.9.1 does not pretend to provide one. Complex reasoning and document work continue through existing deterministic/application capabilities until an actual authorized model is supplied.

## Security and privacy

The model layer cannot grant Android permissions, bypass PrivacyGateway rules, override confirmations, change Evolution XP, access unselected data, or fabricate tool results. Existing execution gateways remain authoritative.

## Hunter Fitness System

JARVIS BETA 2.9.1.1 now includes an offline-first Fitness/Hunter module for home training, calisthenics, and gym training. It stores the training profile, mode, XP, level, goals, session length, weekly training days, and equipment locally on the device. It includes exercise progressions, daily quests, completion XP, and safety-oriented guidance. No account or network connection is required for the core fitness module.
