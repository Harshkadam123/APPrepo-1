# JARVIS 2.9.4 — Resource & Runtime Hardening

## Fixed
- Corrected the user's model directory to `Internal storage/ai model/brain/` (lowercase `ai model`).
- Removed the incorrect alternate `AI Model` preferred path.
- ResourceLocator now removes stale remembered file locations automatically.
- DAILY/CRITICAL resources replace stale locations when a new valid file is discovered.
- ResourceLocator never treats a remembered SAF tree directory as if it were the requested file.
- SAF folder grants are persisted and searched by exact filename.
- Added a folder-grant UI path for Android scoped-storage devices.
- Direct shared-storage crawling remains best-effort; no broad `MANAGE_EXTERNAL_STORAGE` permission was added.
- Qwen model imports now enforce filename, 100 MB–3 GB size bounds, GGUF magic validation, temporary-file cleanup, and internal-storage capacity checks.
- Qwen direct filesystem models are loaded from their actual external path when Android permits access, avoiding unnecessary duplication.
- SAF-discovered models are imported privately because SAF does not provide a stable filesystem path for llama.cpp.
- Qwen load failures now fail safely instead of leaving a broken model session behind.
- Added available-RAM protection before native Qwen loading.
- Made direct PersonalDataTools execution helpers inaccessible; user-facing personal-data actions must pass through ActionExecutor and PrivacyGateway.
- Corrected visible version strings from 2.9.2 to 2.9.3.
- Added `.gitignore` protections for GGUF/model files, local databases, Gradle/build output and IDE files.

## Android storage behavior
JARVIS first checks the remembered valid location, then the preferred path, then previously granted SAF folders, then performs a best-effort shared-storage search. If Android's scoped-storage rules prevent direct crawling, the user can grant the `ai model` folder or select the GGUF file.

## Build note
GitHub Actions/workflows are intentionally not included in this release. They can be added later when APK generation is configured.
