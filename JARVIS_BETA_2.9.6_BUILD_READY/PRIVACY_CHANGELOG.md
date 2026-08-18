# JARVIS BETA 2.3 — Privacy-Hardened Update

## Privacy guarantees in this build

1. The AI/Brain is not given unrestricted Android data APIs.
2. Personal-data capabilities have an explicit `Never / Ask / Allow` policy.
3. Contacts, message bodies, notification contents, photos, file contents, precise/history location, clipboard, credentials, and calendar details default to `NEVER`.
4. Explicit JARVIS memory is opt-in by nature and can be disabled from Privacy.
5. App control requires both the app allow-list and the Privacy Firewall.
6. Privacy `ASK` can turn an otherwise safe capability into a confirmation-gated action.
7. Action-history reading is `NEVER` by default for the AI; the local History tab remains available to the user.
8. Privacy access events for implemented sensitive capabilities are stored locally in Room.
9. Microphone permission is requested only when the user taps Speak, rather than at app startup.
10. No raw microphone audio is persisted by JARVIS.

## Important scope boundary

This build deliberately does NOT implement unrestricted Contacts/SMS/Notifications/Photos/Files/Location/Clipboard/Credentials APIs. They are represented as policy-gated capability definitions so future features must pass through the Privacy Gateway and data-minimization design instead of being added as direct unrestricted calls.

## BETA 2.4 (2026-08-12)
- Added short-lived conversational responses without persisting raw conversation.
- Added bounded local planner and reusable local routines.
- Added local user profile/preferences.
- Added narrow Calendar and Contact lookups behind Android runtime permissions and Privacy Gateway policy.
- Added safe dialer launch; JARVIS does not place calls automatically.
- Added capability map UI.
- Existing blocked capabilities remain blocked by default: messages/notification contents, files, photos, location, clipboard and credentials.
