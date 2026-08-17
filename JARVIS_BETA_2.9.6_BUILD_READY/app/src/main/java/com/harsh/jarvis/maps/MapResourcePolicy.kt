package com.harsh.jarvis.maps

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class MapResourcePolicy(
    val ramCacheMb: Int = 64,
    val diskCacheMb: Int = 512,
    val keepRouteGraphInRam: Boolean = true,
    val preloadVisibleOnly: Boolean = true,
    val loadLabels: Boolean = true,
    val loadBuildings: Boolean = false,
    val loadPoi: Boolean = true
)

internal object MapResourcePolicyStore {
    private const val PREFS = "jarvis_map_resources"
    private const val RAM = "ram_mb"
    private const val DISK = "disk_mb"
    private const val ROUTE_RAM = "route_ram"
    private const val VISIBLE = "visible_only"
    private const val LABELS = "labels"
    private const val BUILDINGS = "buildings"
    private const val POI = "poi"

    fun load(context: Context): MapResourcePolicy {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MapResourcePolicy(
            ramCacheMb = p.getInt(RAM, 64).coerceIn(16, 256),
            diskCacheMb = p.getInt(DISK, 512).coerceIn(64, 8192),
            keepRouteGraphInRam = p.getBoolean(ROUTE_RAM, true),
            preloadVisibleOnly = p.getBoolean(VISIBLE, true),
            loadLabels = p.getBoolean(LABELS, true),
            loadBuildings = p.getBoolean(BUILDINGS, false),
            loadPoi = p.getBoolean(POI, true)
        )
    }

    fun save(context: Context, value: MapResourcePolicy) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(RAM, value.ramCacheMb.coerceIn(16, 256))
            .putInt(DISK, value.diskCacheMb.coerceIn(64, 8192))
            .putBoolean(ROUTE_RAM, value.keepRouteGraphInRam)
            .putBoolean(VISIBLE, value.preloadVisibleOnly)
            .putBoolean(LABELS, value.loadLabels)
            .putBoolean(BUILDINGS, value.loadBuildings)
            .putBoolean(POI, value.loadPoi)
            .apply()
    }
}

internal object CityMapPackManager {
    private const val MAX_DOWNLOAD_MB = 8192L

    fun directory(context: Context): File =
        File(context.filesDir, "osmdroid/city_packs").apply { mkdirs() }

    /**
     * Downloads a user-supplied, legally obtained offline map package.
     * It streams directly to disk and never loads the complete archive into RAM.
     */
    fun download(context: Context, cityName: String, packageUrl: String, maxMb: Int): File? {
        val url = URL(packageUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val declared = connection.contentLengthLong
            val limit = min(maxMb.coerceAtLeast(1).toLong(), MAX_DOWNLOAD_MB) * 1024L * 1024L
            if (declared > limit) return null
            val safeCity = cityName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "city" }
            val suffix = when {
                packageUrl.substringBefore('?').lowercase(Locale.US).endsWith(".mbtiles") -> ".mbtiles"
                packageUrl.substringBefore('?').lowercase(Locale.US).endsWith(".sqlite") -> ".sqlite"
                packageUrl.substringBefore('?').lowercase(Locale.US).endsWith(".gemf") -> ".gemf"
                else -> ".zip"
            }
            val target = File(directory(context), "${safeCity}_${System.currentTimeMillis()}$suffix")
            val temp = File(target.parentFile, target.name + ".part")
            var total = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw IllegalStateException("Map package exceeds ${limit / (1024 * 1024)} MB limit")
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) throw IllegalStateException("Empty map package")
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Could not finalize map package")
            target
        } finally {
            connection.disconnect()
        }
    }

    fun usedBytes(context: Context): Long =
        directory(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun pruneToLimit(context: Context, maxMb: Int): Long {
        val limit = maxMb.coerceAtLeast(64).toLong() * 1024L * 1024L
        var used = usedBytes(context)
        if (used <= limit) return 0L
        val files = directory(context).walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var deleted = 0L
        for (file in files) {
            if (used <= limit) break
            val size = file.length()
            if (file.delete()) {
                used -= size
                deleted += size
            }
        }
        return deleted
    }
}
