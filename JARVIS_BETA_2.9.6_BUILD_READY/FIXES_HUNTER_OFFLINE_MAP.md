# Hunter + Offline Map FIXED

## Fixed
- Swimming now carries leftover metres across sessions (e.g. 60 m + 60 m = 1 level + 20 m remainder).
- Bundled route graph is installed into app-private storage on first launch.
- Route graph can be imported as JSON and the route-engine cache is invalidated.
- Offline map archives are now connected to osmdroid's OfflineTileProvider.
- Users can import legally obtained `.mbtiles`, `.sqlite`, `.zip`, or `.gemf` archives.
- Map remains network-disabled in offline mode.
- Nearest route nodes use a geographic grid index instead of scanning the whole city on every GPS update.
- Generic skill logs are explicitly treated as self-reported completion logs rather than falsely claiming independent verification.
- Activity difficulty (1–5) changes XP in a controlled way.
- SSS+ Competitive ML is a one-achievement gate and requires competition name, result/rank, and evidence reference.
- Competition evidence is stored locally; JARVIS does not claim independent verification.

## Data note
The bundled `route_graph.json` is a small demo graph for testing the routing pipeline. It is not a real city map. Replace it with a legally obtained city/office routing graph for real navigation.

Actual map imagery/tiles are not bundled. Import a legally obtained offline map archive from the Map screen.


## Map city download + RAM policy
- City packs are downloaded as user-supplied HTTPS files and streamed directly to private storage; the entire package is never held in RAM.
- The user can set a disk limit and the app prunes oldest city-pack files when the limit is exceeded.
- osmdroid's in-memory tile cache is explicitly bounded by a user-selected RAM budget using its cacheMapTileCount/overshoot settings.
- Only visible map tiles are intended to be rendered; a whole city is never decoded into RAM.
- The active route graph is the only large non-tile object intentionally kept in RAM, and the user can disable that policy.
- Labels, POIs, and buildings are independently selectable as app-level layers.
- "Open city in Google Maps" is provided for city lookup/reference only. JARVIS does not download or redistribute Google Maps tiles/data. Use a legally obtained offline package from a provider that permits downloading.
