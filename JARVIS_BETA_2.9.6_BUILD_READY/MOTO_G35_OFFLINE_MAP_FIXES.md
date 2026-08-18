# Moto G35 / Offline Map fixes

- GPS-only location updates for offline operation; no NETWORK_PROVIDER dependency.
- osmdroid cache is placed in app-private external files storage.
- Network tile use is disabled on the map screen.
- Added offline destination latitude/longitude and distance/bearing navigation.
- Added explicit Java/Kotlin JVM 17 target.
- Fixed an existing Kotlin compile error in `JarvisApp.kt` caused by reading
  `policyRevision` before its declaration.
- Added release shrinker keep rules for JARVIS and osmdroid.
- Added a small offline-map data contract under `assets/offline_maps/` rather than
  pretending a city dataset exists when none is present in the source archive.

No existing JARVIS tabs or assistant behavior were intentionally removed.
