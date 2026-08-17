JARVIS offline maps

Google Maps:
- JARVIS may open a city search in Google Maps for reference.
- JARVIS does NOT download or redistribute Google Maps tiles/data.

Offline city packs:
- Import or download a legally obtained offline package in MBTiles, SQLite, ZIP, or GEMF form.
- City packs are streamed to private storage and are never loaded as one giant byte array into RAM.

RAM/storage:
- Map RAM cache is bounded by the Map Memory settings.
- Disk cache has a configurable limit and old city packs can be pruned.
- The full city is never decoded into RAM; only the active tile cache is held in memory.

Routing:
- route_graph.json is a routing graph, not map imagery.
- Replace the demo graph with a legally obtained city/office routing graph for real road navigation.
