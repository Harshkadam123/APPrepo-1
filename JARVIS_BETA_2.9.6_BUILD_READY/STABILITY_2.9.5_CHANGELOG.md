# JARVIS 2.9.5 Stability Hardening

- Raised Qwen3 minimum available RAM gate from 3 GB to 5 GB.
- Added Android 13+ POST_NOTIFICATIONS runtime request.
- Added notification availability guards before posting alarms/proactive notifications.
- Added exact-alarm availability/settings affordance for proactive scheduling.
- Added bounded SAF tree traversal to ResourceLocator (12,000 nodes / 8 levels).
- Removed FocusManager non-null assertions in persisted preference reads.
- Hardened OfflineRouteEngine against inconsistent/missing route nodes.
- Corrected blocked communications/privacy error text.
- Added safer exact-alarm scheduling fallback.

## Verification

- ZIP/source integrity checked.
- Gradle compilation was attempted; this environment could not download Gradle 8.9 because `services.gradle.org` DNS/network access is unavailable, so no successful compile result is claimed.
