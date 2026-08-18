# JARVIS BETA 2.9 Model Inventory

| Model | Format | Size | Quantization | Runtime | Role | RAM estimate | Loading |
|---|---|---:|---|---|---|---:|---|
| PersonalIntentModel | JSON Naive-Bayes statistics | ~24 KB | N/A | Kotlin/JVM deterministic math | Intent + extraction | <8 MB | On demand / reusable while active |

## Findings from BETA 2.8.1

No additional model binaries or native inference runtimes were present. In particular, the source tree did not contain GGUF, ONNX, TFLite/LiteRT or transformer model weights. Therefore BETA 2.9 improves the routing/orchestration layer without pretending that a larger model exists.

## Routing policy

- Intent/tool command → PersonalIntentModel when classification is useful.
- Simple deterministic command → rule/entity path first.
- Conversation/reasoning/document work → existing deterministic capability only unless an authorized model is actually available.
- Tool result interpretation → existing tool result is authoritative; model text cannot replace it.
- Memory pressure → avoid heavy inference, reduce concurrency, or fall back to deterministic behavior.


## 2.9.2 Local Generative Brain

- **Qwen3-1.7B-Q4_K_M.gguf**
- Role: CONVERSATION / REASONING fallback for natural-language responses
- Runtime: llama.cpp through `org.codeshipping:llama-kotlin-android:0.1.7`
- Default path: `/storage/emulated/0/AI Model/brain/Qwen3-1.7B-Q4_K_M.gguf`
- Context: 2048 tokens
- Threads: 4 CPU threads
- Quantization: Q4_K_M
- Loading: on demand, mmap enabled
- Network: none during inference
- Tool execution: **never delegated to the LLM**; ActionExecutor remains authoritative
