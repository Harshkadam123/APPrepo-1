# JARVIS 2.9.3 — Resource Locator

## Purpose
JARVIS now has a generic resource-location system instead of hard-coding one path for important files.

## Qwen3 path
The user's exact preferred path is:

`Internal storage/ai model/brain/Qwen3-1.7B-Q4_K_M.gguf`

Android paths are case-sensitive, so the lowercase `ai model` path is the first preferred location. The the canonical external path is lowercase `ai model`; no uppercase fallback is assumed.

## Resolution priority
For daily/critical resources JARVIS:
1. checks the last remembered valid location;
2. checks preferred paths;
3. searches previously granted storage folders by filename;
4. searches accessible shared storage by filename;
5. replaces the stale remembered location when a better/new valid location is found.

Daily and critical resources remember the new location. Normal and low-priority resources can be resolved without permanently storing their location.

## Android storage
Because Android scoped storage can block recursive filesystem access, JARVIS can retain access to a user-selected storage folder through a persisted Storage Access Framework permission. The model is still copied to app-private storage for llama.cpp inference when necessary.

## Safety
Search is bounded by depth and file-count limits. JARVIS never executes a discovered file; it only resolves resources by name.
