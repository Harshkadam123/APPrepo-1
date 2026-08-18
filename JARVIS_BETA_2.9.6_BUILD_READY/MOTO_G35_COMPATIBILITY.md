# Moto G35 compatibility

The JARVIS 2.9 project is configured for modern Android devices such as the Motorola moto g35 5G.

- `minSdk 29` and `targetSdk 35`.
- Uses Android location APIs and osmdroid instead of requiring Google Maps services.
- Map rendering disables data connections for offline mode.
- No always-on location service is used by the map screen.
- Multi-touch map controls work with the moto g35 touchscreen.
- Hardware acceleration is enabled for the map UI.

Important: offline city tiles must be supplied/cached for a city to display detailed map imagery while fully offline. GPS itself does not require mobile data.
