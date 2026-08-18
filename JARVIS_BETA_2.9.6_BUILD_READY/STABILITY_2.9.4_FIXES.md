# JARVIS 2.9.4 — Stability Fixes

- Version corrected to 2.9.4 / versionCode 294.
- Qwen status resolution is asynchronous and cached; Compose no longer performs filesystem searches during rendering.
- ResourceLocator stale-path replacement remains enabled for DAILY/CRITICAL resources.
- SAF/granted-folder lookup remains the reliable modern-Android fallback; no MANAGE_EXTERNAL_STORAGE permission added.
- Accessibility service declaration uses BIND_ACCESSIBILITY_SERVICE with exported=true for Android service discovery.
- Qwen memory guard now uses a conservative runtime estimate: max(3 GiB, 1.5× GGUF size + 512 MiB).
- Qwen load failures preserve a short diagnostic reason for local troubleshooting while showing safe user-facing status.
- Qwen3 prompt uses a dedicated `/no_think` line in the user turn and retains output sanitization.
- UI labels/messages updated to 2.9.4.
- GitHub Actions/workflows intentionally not included.
