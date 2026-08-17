package com.harsh.jarvis.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavigationInfo(
    val distanceMeters: Float,
    val bearingDegrees: Float
)

private fun navigationInfo(from: Location, to: GeoPoint): NavigationInfo {
    val results = FloatArray(3)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
    return NavigationInfo(results[0], (results[1] + 360f) % 360f)
}

private fun formatDistance(meters: Float): String = when {
    meters < 1000f -> "${meters.roundToInt()} m"
    else -> "${"%.2f".format(meters / 1000f)} km"
}

private fun cardinalDirection(degrees: Float): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[((degrees + 22.5f) / 45f).toInt() % 8]
}

@Composable
fun OfflineMapScreen() {
    val context = LocalContext.current
    var locationEnabled by remember { mutableStateOf(false) }
    var gpsAvailable by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var destinationLat by remember { mutableStateOf("") }
    var destinationLon by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var navigation by remember { mutableStateOf<NavigationInfo?>(null) }
    var mapStatus by remember { mutableStateOf("Offline map: local/cached tiles only") }
    var route by remember { mutableStateOf<OfflineRoute?>(null) }
    var gpsDisabled by remember { mutableStateOf(false) }
    var routeData by remember { mutableStateOf<Pair<Map<String, RouteNode>, Map<String, List<RouteEdge>>>>(emptyMap<String, RouteNode>() to emptyMap()) }
    var offlineArchives by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var archiveImported by remember { mutableStateOf(false) }
    var routeGraphVersion by remember { mutableIntStateOf(0) }
    var offlineProviderInstalled by remember { mutableStateOf(false) }
    var showMapSettings by remember { mutableStateOf(false) }
    var showCityDownloader by remember { mutableStateOf(false) }
    var resourcePolicy by remember { mutableStateOf(MapResourcePolicyStore.load(context)) }
    var cityName by remember { mutableStateOf("") }
    var mapPackageUrl by remember { mutableStateOf("") }
    var downloadStatus by remember { mutableStateOf("") }
    var cityMapUsedMb by remember { mutableLongStateOf(0L) }
    var mapBusy by remember { mutableStateOf(false) }
    val mapScope = rememberCoroutineScope()

    val routeGraphPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mapBusy = true
            mapScope.launch {
                val imported = withContext(Dispatchers.IO) { OfflineMapArchiveManager.importRouteGraph(context, uri) }
                if (imported != null) {
                    OfflineRouteEngine.clearCache()
                    routeGraphVersion++
                    mapStatus = "Routing graph imported. Reloading..."
                } else {
                    mapStatus = "Invalid routing graph JSON."
                }
                mapBusy = false
            }
        }
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mapBusy = true
            mapScope.launch {
                val imported = withContext(Dispatchers.IO) { OfflineMapArchiveManager.importArchive(context, uri) }
                if (imported != null) {
                    archiveImported = true
                    offlineProviderInstalled = false
                    offlineArchives = (offlineArchives + imported).distinctBy { it.absolutePath }
                    mapStatus = "Offline map archive imported: ${imported.name}"
                } else {
                    mapStatus = "Unsupported offline map file. Use .mbtiles, .sqlite, .zip or .gemf."
                }
                mapBusy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationEnabled = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!locationEnabled) mapStatus = "Precise location permission is required for offline GPS navigation."
    }

    LaunchedEffect(Unit) {
        offlineArchives = (OfflineMapArchiveManager.installBundledArchives(context) + OfflineMapArchiveManager.allArchives(context)).distinctBy { it.absolutePath }
        // Install the optional bundled/demo graph as a real route_graph.json so the
        // routing engine can load it on a fresh install.
        val graphAsset = "offline_maps/route_graph.json"
        val graphFile = java.io.File(context.filesDir, "offline_maps/route_graph.json")
        graphFile.parentFile?.mkdirs()
        if (!graphFile.exists()) {
            runCatching {
                context.assets.open(graphAsset).use { input -> graphFile.outputStream().use { input.copyTo(it) } }
            }
        }
        val base = java.io.File(context.filesDir, "osmdroid").apply { mkdirs() }
        val tileDir = java.io.File(base, "tiles")
        tileDir.mkdirs()
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            osmdroidBasePath = base
            osmdroidTileCache = tileDir
            userAgentValue = context.packageName

            // Bound osmdroid's in-memory tile count. A tile is roughly 256x256x4 bytes
            // before bitmap/object overhead, so reserve only ~60% of the user-selected
            // RAM budget for the tile cache itself.
            val bytesPerTile = 256L * 256L * 4L
            val tileCount = ((resourcePolicy.ramCacheMb.toLong() * 1024L * 1024L * 0.60) / bytesPerTile)
                .toInt().coerceIn(9, 1024)
            setCacheMapTileCount(tileCount.toShort())
            setCacheMapTileOvershoot((tileCount / 8).coerceIn(1, 128).toShort())

            val diskMax = resourcePolicy.diskCacheMb.toLong() * 1024L * 1024L
            setTileFileSystemCacheMaxBytes(diskMax)
            setTileFileSystemCacheTrimBytes((diskMax * 0.90).toLong())
        }
        locationEnabled = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(routeGraphVersion) {
        routeData = withContext(Dispatchers.IO) { OfflineRouteEngine.load(context) }
        mapStatus = if (routeData.first.isNotEmpty()) "Offline routing graph ready; add a legal map archive for detailed tiles" else "Offline map: add a local map archive for tiles and a route graph for road routing"
    }

    LaunchedEffect(offlineArchives, mapView, archiveImported) {
        val view = mapView
        if (view != null && offlineArchives.isNotEmpty() && !offlineProviderInstalled) {
            runCatching {
                val provider = OfflineTileProvider(
                    SimpleRegisterReceiver(context),
                    offlineArchives.toTypedArray()
                )
                view.setTileProvider(provider)
                val sources = provider.archives.firstOrNull()?.tileSources.orEmpty()
                if (sources.isNotEmpty()) {
                    view.setTileSource(FileBasedTileSource.getSource(sources.first()))
                } else {
                    // MBTiles commonly has no tile-source name; MAPNIK is still a valid
                    // tile source for raster MBTiles when the archive contains image tiles.
                    view.setTileSource(TileSourceFactory.MAPNIK)
                }
                view.setUseDataConnection(false)
                view.invalidate()
                offlineProviderInstalled = true
                mapStatus = "Offline map provider ready (${offlineArchives.size} archive${if (offlineArchives.size == 1) "" else "s"})"
            }.onFailure {
                offlineProviderInstalled = false
                mapStatus = "Offline archive found but could not be opened: ${it.message ?: "unknown error"}"
            }
        }
    }

    LaunchedEffect(currentLocation, destination, routeData) {
        val from = currentLocation; val to = destination
        navigation = if (from != null && to != null) navigationInfo(from, to) else null
        route = if (from != null && to != null) {
            withContext(Dispatchers.Default) {
                OfflineRouteEngine.route(routeData.first, routeData.second, GeoPoint(from.latitude, from.longitude), to)
            }
        } else null
        mapView?.let { view ->
            view.overlays.removeAll { it is Polyline }
            route?.let { r ->
                val line = Polyline(view).apply { setPoints(r.points) }
                view.overlays.add(line)
            }
            view.invalidate()
        }
    }

    LaunchedEffect(showMapSettings, offlineArchives) {
        if (showMapSettings) cityMapUsedMb = withContext(Dispatchers.IO) { CityMapPackManager.usedBytes(context) / (1024 * 1024) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                if (!locationEnabled) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    currentLocation?.let { mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude)) }
                }
            }, modifier = Modifier.weight(1f)) {
                Text(if (locationEnabled) "My Location" else "Enable Location")
            }
            OutlinedButton(onClick = {
                mapView?.controller?.setZoom(12.0)
            }, modifier = Modifier.weight(1f)) { Text("City View") }
            OutlinedButton(onClick = {
                archivePicker.launch(arrayOf("*/*"))
            }, modifier = Modifier.weight(1f)) { Text("Import Map") }
            OutlinedButton(onClick = {
                routeGraphPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            }, modifier = Modifier.weight(1f)) { Text("Import Route") }
            OutlinedButton(onClick = { showMapSettings = true }, modifier = Modifier.weight(1f)) { Text("Map Memory") }
            OutlinedButton(onClick = { showCityDownloader = true }, modifier = Modifier.weight(1f)) { Text("City Pack") }
        }

        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("Offline navigator", style = MaterialTheme.typography.titleSmall)
                Text(mapStatus)
                if (offlineArchives.isEmpty()) {
                    Text("No offline map archive installed. Import a legally obtained .mbtiles/.sqlite/.zip/.gemf map.", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (gpsAvailable) "GPS: available" else if (gpsDisabled) "GPS is disabled — enable location services" else "GPS: waiting for satellite fix")
                if (gpsDisabled) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open Location Settings") }
                navigation?.let {
                    Text("Destination: ${formatDistance(it.distanceMeters)} • ${cardinalDirection(it.bearingDegrees)} (${it.bearingDegrees.roundToInt()}°)")
                }
                route?.let { r ->
                    Text("Offline road route: ${formatDistance(r.distanceMeters.toFloat())}")
                    r.instructions.take(5).forEachIndexed { index, instruction ->
                        Text("${index + 1}. ${instruction.text} • ${formatDistance(instruction.distanceMeters.toFloat())}")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = destinationLat,
                onValueChange = { destinationLat = it },
                label = { Text("Latitude") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = destinationLon,
                onValueChange = { destinationLon = it },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val lat = destinationLat.toDoubleOrNull()
                val lon = destinationLon.toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    val point = GeoPoint(lat, lon)
                    destination = point
                    mapView?.let { view ->
                        view.overlays.removeAll { it is Marker }
                        view.overlays.removeAll { it is Polyline }
                        Marker(view).apply { position = point; title = "Destination"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }.also { view.overlays.add(it) }
                    }
                    mapView?.controller?.animateTo(point)
                    mapStatus = "Destination set. Offline routing will use a local route graph when installed; otherwise distance/bearing is used."
                } else {
                    mapStatus = "Enter a valid latitude (-90..90) and longitude (-180..180)."
                }
            }, modifier = Modifier.weight(1f)) { Text("Set Destination") }
            OutlinedButton(onClick = {
                destination = null
                navigation = null
                route = null
                mapView?.overlays?.removeAll { it is Marker }
                mapView?.overlays?.removeAll { it is Polyline }
                mapStatus = "Offline map: waiting for local/cached tiles"
            }, modifier = Modifier.weight(1f)) { Text("Clear") }
        }


        if (showMapSettings) {
            AlertDialog(
                onDismissRequest = { showMapSettings = false },
                title = { Text("Map RAM & storage") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The entire city map is never loaded into RAM. osmdroid renders visible tiles and keeps a bounded cache.")
                        Text("RAM tile-cache budget: ${resourcePolicy.ramCacheMb} MB")
                        Slider(
                            value = resourcePolicy.ramCacheMb.toFloat(),
                            onValueChange = { value ->
                                resourcePolicy = resourcePolicy.copy(ramCacheMb = (value / 16f).roundToInt() * 16)
                            },
                            valueRange = 16f..256f,
                            steps = 14
                        )
                        Text("Offline map storage limit: ${resourcePolicy.diskCacheMb} MB")
                        Slider(
                            value = resourcePolicy.diskCacheMb.toFloat(),
                            onValueChange = { value ->
                                resourcePolicy = resourcePolicy.copy(diskCacheMb = (value / 64f).roundToInt() * 64)
                            },
                            valueRange = 64f..2048f,
                            steps = 30
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = resourcePolicy.keepRouteGraphInRam, onCheckedChange = { resourcePolicy = resourcePolicy.copy(keepRouteGraphInRam = it) })
                            Text("Keep active route graph in RAM")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = resourcePolicy.preloadVisibleOnly, onCheckedChange = { resourcePolicy = resourcePolicy.copy(preloadVisibleOnly = it) })
                            Text("Load visible area only (recommended)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = resourcePolicy.loadLabels, onCheckedChange = { resourcePolicy = resourcePolicy.copy(loadLabels = it) })
                            Text("Labels")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = resourcePolicy.loadBuildings, onCheckedChange = { resourcePolicy = resourcePolicy.copy(loadBuildings = it) })
                            Text("Buildings / 3D data")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = resourcePolicy.loadPoi, onCheckedChange = { resourcePolicy = resourcePolicy.copy(loadPoi = it) })
                            Text("Points of interest")
                        }
                        Text("Disk used by city map packs: $cityMapUsedMb MB")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        MapResourcePolicyStore.save(context, resourcePolicy)
                        val cfg = Configuration.getInstance()
                        val bytesPerTile = 256L * 256L * 4L
                        val tileCount = ((resourcePolicy.ramCacheMb.toLong() * 1024L * 1024L * 0.60) / bytesPerTile)
                            .toInt().coerceIn(9, 1024)
                        cfg.setCacheMapTileCount(tileCount.toShort())
                        cfg.setCacheMapTileOvershoot((tileCount / 8).coerceIn(1, 128).toShort())
                        val diskMax = resourcePolicy.diskCacheMb.toLong() * 1024L * 1024L
                        cfg.setTileFileSystemCacheMaxBytes(diskMax)
                        cfg.setTileFileSystemCacheTrimBytes((diskMax * 0.90).toLong())
                        mapScope.launch {
                            withContext(Dispatchers.IO) { CityMapPackManager.pruneToLimit(context, resourcePolicy.diskCacheMb) }
                            cityMapUsedMb = withContext(Dispatchers.IO) { CityMapPackManager.usedBytes(context) / (1024 * 1024) }
                        }
                        showMapSettings = false
                        mapStatus = "Map memory/storage policy saved. Reopen the map tab to fully recreate the tile cache."
                    }) { Text("Save") }
                }
            )
        }

        if (showCityDownloader) {
            AlertDialog(
                onDismissRequest = { showCityDownloader = false },
                title = { Text("Offline city map pack") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a city name and a direct URL to a legally obtained offline map package. Google Maps itself is not downloaded; its map tiles/data are not redistributed by JARVIS.")
                        OutlinedTextField(value = cityName, onValueChange = { cityName = it }, label = { Text("City name") }, singleLine = true)
                        OutlinedTextField(value = mapPackageUrl, onValueChange = { mapPackageUrl = it }, label = { Text("Map package URL") }, singleLine = true)
                        Text("Maximum download: ${resourcePolicy.diskCacheMb} MB. The file is streamed to disk, not loaded into RAM.")
                        if (downloadStatus.isNotBlank()) Text(downloadStatus)
                        TextButton(onClick = {
                            val q = Uri.encode(cityName.trim())
                            if (q.isNotBlank()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
                                }
                            }
                        }) { Text("Open city in Google Maps") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val city = cityName.trim()
                        val url = mapPackageUrl.trim()
                        if (city.isEmpty() || !url.startsWith("https://")) {
                            downloadStatus = "Use a city name and an HTTPS map-package URL."
                        } else {
                            downloadStatus = "Downloading..."
                            mapScope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    CityMapPackManager.download(context, city, url, resourcePolicy.diskCacheMb)
                                }
                                if (file != null) {
                                    withContext(Dispatchers.IO) { CityMapPackManager.pruneToLimit(context, resourcePolicy.diskCacheMb) }
                                    offlineArchives = withContext(Dispatchers.IO) { OfflineMapArchiveManager.allArchives(context) }
                                    cityMapUsedMb = withContext(Dispatchers.IO) { CityMapPackManager.usedBytes(context) / (1024 * 1024) }
                                    offlineProviderInstalled = false
                                    archiveImported = true
                                    downloadStatus = "Downloaded ${file.name}"
                                    mapStatus = "Offline city pack ready"
                                } else {
                                    downloadStatus = "Download failed or package exceeded the storage limit."
                                }
                            }
                        }
                    }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { showCityDownloader = false }) { Text("Close") } }
            )
        }

        if (mapBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapView = this
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    controller.setZoom(12.0)
                    // Core map operation is explicitly offline. Existing cached/local tiles can still render.
                    setUseDataConnection(false)
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                        runCatching { locationOverlay.enableMyLocation() }.onSuccess { overlays.add(locationOverlay) }
                    }
                    mapStatus = "Offline map: network disabled; cached/local tiles only"
                }
            },
            update = { view ->
                mapView = view
                currentLocation?.let {
                    if (destination == null) view.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                }
                view.invalidate()
            }
        )
    }

    LaunchedEffect(locationEnabled, mapView) {
        val view = mapView ?: return@LaunchedEffect
        if (locationEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val overlay = view.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                ?: MyLocationNewOverlay(GpsMyLocationProvider(context), view).also { view.overlays.add(it) }
            runCatching { overlay.enableMyLocation() }
            view.invalidate()
        }
    }

    DisposableEffect(locationEnabled) {
        if (!locationEnabled) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                gpsAvailable = location.provider == LocationManager.GPS_PROVIDER
                destination?.let { navigation = navigationInfo(location, it) }
            }
            override fun onProviderEnabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) { gpsAvailable = true; gpsDisabled = false } }
            override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) { gpsAvailable = false; gpsDisabled = true } }
            @Deprecated("Deprecated by Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                gpsDisabled = !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                // GPS only: no network provider is required for offline operation.
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
                gpsAvailable = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                gpsDisabled = !gpsAvailable
            }
        } catch (_: SecurityException) { }
        onDispose { lm.removeUpdates(listener) }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) { androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView?.onResume(); androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView?.onPause(); else -> Unit }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView?.onPause(); mapView?.onDetach() }
    }
}
