# JARVIS BETA 2.9 — AI / MODEL LAYER UPDATE

BETA 2.9 is an AI/model-only incremental update from BETA 2.8.1.

## Scope

No new user-facing feature, Android permission, accessibility capability, privacy capability, autonomous action, or UI system is introduced. Existing Maps/GPS, Security Scanner, Evolution, Proactive Intelligence, Communication, PDF, Voice, Task and Android-control behavior is preserved.

## Model inventory

BETA 2.8.1 shipped one actual AI asset:

- `intent_model.json`
- JSON Naive-Bayes statistics
- approximately 24 KB asset size
- no quantization (not a neural tensor model)
- lightweight CPU inference
- estimated runtime footprint under 8 MB
- deterministic intent classification plus rule-based entity extraction

No GGUF, ONNX, TFLite/LiteRT, transformer or generative LLM asset was present in the BETA 2.8.1 project. BETA 2.9 does not invent or claim one.

## Improvements

- Central `ModelSpec` inventory and task/role taxonomy.
- Deterministic `ModelRouter` that prefers the smallest compatible model.
- On-demand `ModelSession` for future heavy models.
- Android memory-pressure policy for avoiding heavy inference under low RAM.
- Graceful model fallback architecture; deterministic tool logic remains the final fallback.
- Expanded natural-language paraphrase handling for existing intents.
- Tool-grounding policy mapping intents to existing authorized systems.
- Short-lived conversation topic state without persistent personal-data expansion.
- Internal benchmark result structures for latency, loading, unloading, confidence, success and RAM.
- No model is kept loaded merely because another capability exists.

## Safety boundary

The model layer cannot grant permissions, bypass privacy, send communications, change XP, override confirmations, or fabricate tool results. Existing execution gateways remain authoritative.

## Offline behavior

The shipped intent layer remains fully local. If a future optional generative model is unavailable, BETA 2.9 continues through deterministic intent/tool behavior instead of claiming an unavailable capability.
