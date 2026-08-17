package com.harsh.jarvis.maps

import android.content.Context
import java.io.File

/** Installs supported offline map archives and the optional local routing graph into private app storage. */
internal object OfflineMapArchiveManager {
    private val supported = setOf("mbtiles", "sqlite", "zip", "gemf")
    private const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L * 1024L
    private const val MAX_ROUTE_GRAPH_BYTES = 64L * 1024L * 1024L

    fun installBundledArchives(context: Context): List<File> {
        val base = File(context.filesDir, "osmdroid").apply { mkdirs() }
        val assetRoot = "offline_maps"
        val names = runCatching { context.assets.list(assetRoot).orEmpty().toList() }.getOrDefault(emptyList())
        return names.filter { name -> supported.any { name.lowercase().endsWith(".$it") } }.mapNotNull { name ->
            runCatching {
                val target = File(base, name)
                if (!target.exists() || target.length() == 0L) {
                    context.assets.open("$assetRoot/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
                }
                target
            }.getOrNull()
        }
    }

    /** Copies a user-selected archive into osmdroid's private archive directory. */
    fun importRouteGraph(context: Context, source: android.net.Uri): File? {
        val target = File(context.filesDir, "offline_maps/route_graph.json")
        target.parentFile?.mkdirs()
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(source, "r")?.use { afd ->
                if (afd.length >= 0 && afd.length > MAX_ROUTE_GRAPH_BYTES) throw IllegalStateException("Route graph is larger than 64 MB")
            }
            val text = context.contentResolver.openInputStream(source)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size.toLong() > MAX_ROUTE_GRAPH_BYTES) throw IllegalStateException("Route graph is larger than 64 MB")
                bytes.toString(Charsets.UTF_8)
            } ?: return null
            val json = org.json.JSONObject(text)
            json.getJSONArray("nodes")
            json.getJSONArray("edges")
            val temp = File(target.parentFile, "route_graph.json.part")
            temp.writeText(text)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Could not finalize route graph")
            target
        }.getOrElse {
            File(target.parentFile, "route_graph.json.part").delete()
            null
        }
    }

    fun importArchive(context: Context, source: android.net.Uri): File? {
        val name = (source.lastPathSegment ?: "offline-map").substringAfterLast('/').ifBlank { "offline-map" }
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in supported) return null
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetDir = userArchiveDirectory(context)
        val target = File(targetDir, safeName)
        val temp = File(targetDir, "$safeName.part")
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(source, "r")?.use { afd ->
                if (afd.length >= 0 && afd.length > MAX_IMPORT_BYTES) throw IllegalStateException("Map archive is larger than 8 GB")
            }
            var total = 0L
            context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) throw IllegalStateException("Map archive is larger than 8 GB")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            if (total == 0L) throw IllegalStateException("Empty map archive")
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Could not finalize map archive")
            target
        }.getOrElse {
            temp.delete()
            null
        }
    }

    fun userArchiveDirectory(context: Context): File = File(context.filesDir, "osmdroid").apply { mkdirs() }

    fun allArchives(context: Context): List<File> {
        val dirs = listOf(userArchiveDirectory(context), CityMapPackManager.directory(context))
        return dirs.flatMap { dir ->
            dir.listFiles().orEmpty().filter { file ->
                file.isFile && supported.any { file.name.lowercase().endsWith(".$it") }
            }
        }.distinctBy { it.absolutePath }
    }
}
