# JARVIS 2.9.2 — Qwen3 Local Brain

## Added

- Qwen3-1.7B-Q4_K_M.gguf local generative brain via llama.cpp/llama-kotlin-android.
- Default model location: `/storage/emulated/0/AI Model/brain/Qwen3-1.7B-Q4_K_M.gguf`.
- Android system file picker fallback for scoped-storage devices.
- Imported model is stored in JARVIS private storage after selection.
- Qwen3 is used for natural conversation and otherwise-unclassified requests.
- Tool/action execution remains deterministic and protected by ActionExecutor + PrivacyGateway.
- Qwen3 prompt uses ChatML and `/no_think` for lower latency on mobile.
- 2048-token context, 4 CPU threads, mmap enabled, CPU inference for broad Moto G35 compatibility.

## Safety boundary

Qwen3 never directly opens apps, calls contacts, sends messages, reads calendar, changes privacy settings, or performs device actions. It only produces text. JARVIS's deterministic action layer remains the authority for execution.

## Model storage

The GGUF is **not bundled in the APK**. This keeps the APK practical and lets the user keep the model in shared storage. If Android scoped storage blocks direct access to the preferred path, select the same GGUF once through the in-app picker.

## Important

The first model load can take significant time and RAM. If the device reports low memory, JARVIS should not keep the model resident while other memory-heavy work is active.

## Privacy note

Saved JARVIS memory is only injected into Qwen3 when `JARVIS_MEMORY` is explicitly `ALLOW`. If that capability is `ASK` or `NEVER`, the LLM receives no saved-memory context.
