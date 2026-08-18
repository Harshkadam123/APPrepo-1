# JARVIS 2.9.6

JARVIS uses Qwen3-1.7B-Q4_K_M.gguf as its optional local generative brain. The preferred user location is `Internal storage/ai model/brain/Qwen3-1.7B-Q4_K_M.gguf`. ResourceLocator searches remembered, preferred and user-granted locations and replaces stale locations automatically.

2.9.6 stability hardening includes bounded background storage scans, permission-safe offline maps, background map import/routing work, serialized Qwen inference, adaptive native-load diagnostics with a 5 GB available-RAM safety floor, Android 13+ notification handling, exact-alarm/time-change rescheduling, safe full-screen notification fallback, and safer contact disambiguation.

The GGUF model is intentionally not bundled into the APK.
