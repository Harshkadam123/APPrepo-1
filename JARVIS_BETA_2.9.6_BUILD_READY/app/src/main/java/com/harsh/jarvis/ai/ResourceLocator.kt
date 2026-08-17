package com.harsh.jarvis.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

/**
 * Generic local resource resolver used by JARVIS for models, maps and other user files.
 * Resolution is intentionally synchronous only when called from a background dispatcher.
 * UI callers should use a cached status or call resolve() from Dispatchers.IO.
 */
class ResourceLocator(private val context: Context) {
    companion object {
        private const val PREFS = "jarvis_resource_locator"
        private const val KEY_PREFIX = "resource_"
        private const val TREE_PREFIX = "tree_"
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 12_000
        private const val MAX_SAF_NODES = 12_000
        const val STORAGE_TREE_PREFIX = "tree:"
    }

    enum class Priority(val score: Int, val rememberLocation: Boolean) {
        CRITICAL(100, true), DAILY(90, true), NORMAL(50, false), LOW(10, false)
    }

    data class ResourceSpec(val key: String, val fileName: String, val priority: Priority, val preferredPaths: List<String> = emptyList())
    data class FoundResource(val location: String, val isUri: Boolean, val source: String)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(spec: ResourceSpec, location: String) {
        if (spec.priority.rememberLocation) prefs.edit().putString(KEY_PREFIX + spec.key, location).apply()
    }

    fun forget(spec: ResourceSpec) { prefs.edit().remove(KEY_PREFIX + spec.key).apply() }

    fun remembered(spec: ResourceSpec): String? {
        val raw = prefs.getString(KEY_PREFIX + spec.key, null) ?: return null
        if (isUsableResourceLocation(raw, spec.fileName)) return raw
        prefs.edit().remove(KEY_PREFIX + spec.key).apply()
        return null
    }

    /** Full resolution. Call from Dispatchers.IO, never directly during Compose rendering. */
    fun resolve(spec: ResourceSpec): FoundResource? {
        remembered(spec)?.let { return FoundResource(it, it.startsWith("content:"), "remembered") }
        for (path in spec.preferredPaths.distinct()) {
            val file = File(path)
            if (file.isFile && file.canRead() && file.name.equals(spec.fileName, ignoreCase = true)) {
                return FoundResource(file.absolutePath, false, "preferred-path").also { remember(spec, it.location) }
            }
        }
        findInGrantedTrees(spec.fileName)?.let { return it.also { remember(spec, it.location) } }
        // Best effort only; scoped storage may make this unavailable. SAF remains the reliable path.
        val found = findFileByName(Environment.getExternalStorageDirectory(), spec.fileName)
        if (found != null) return FoundResource(found.absolutePath, false, "shared-storage-search").also { remember(spec, it.location) }
        return null
    }

    fun addGrantedTree(uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val readPersistable = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, readPersistable)
        } catch (_: SecurityException) {
            val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            resolver.takePersistableUriPermission(uri, readWrite)
        }
        val root = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching false
        if (!root.isDirectory || !root.canRead()) return@runCatching false
        val key = KEY_PREFIX + TREE_PREFIX + uri.toString().hashCode().toUInt().toString(16)
        prefs.edit().putString(key, STORAGE_TREE_PREFIX + uri).apply()
        true
    }.getOrDefault(false)

    private fun findInGrantedTrees(fileName: String): FoundResource? {
        val keys = prefs.all.keys.filter { it.startsWith(KEY_PREFIX + TREE_PREFIX) }
        for (key in keys) {
            val raw = prefs.getString(key, null) ?: continue
            if (!raw.startsWith(STORAGE_TREE_PREFIX)) continue
            val uri = Uri.parse(raw.removePrefix(STORAGE_TREE_PREFIX))
            val root = DocumentFile.fromTreeUri(context, uri)
            if (root == null || !root.isDirectory) { prefs.edit().remove(key).apply(); continue }
            findDocumentFile(root, fileName, 0, intArrayOf(0))?.let { return FoundResource(it.uri.toString(), true, "granted-folder-search") }
        }
        return null
    }

    private fun findDocumentFile(node: DocumentFile, fileName: String, depth: Int, visited: IntArray): DocumentFile? {
        if (depth > MAX_DEPTH || visited[0] >= MAX_SAF_NODES) return null
        visited[0]++
        if (node.isFile && node.name?.equals(fileName, ignoreCase = true) == true) return node
        if (!node.isDirectory) return null
        val children = runCatching { node.listFiles() }.getOrNull() ?: return null
        for (child in children) {
            if (visited[0] >= MAX_SAF_NODES) return null
            visited[0]++
            if (child.isFile && child.name?.equals(fileName, ignoreCase = true) == true) return child
            if (child.isDirectory) findDocumentFile(child, fileName, depth + 1, visited)?.let { return it }
        }
        return null
    }

    private fun findFileByName(root: File, fileName: String): File? {
        if (!root.exists() || !root.isDirectory || !root.canRead()) return null
        var visited = 0
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.add(root to 0)
        val wanted = fileName.lowercase(Locale.ROOT)
        val seen = HashSet<String>()
        while (stack.isNotEmpty() && visited < MAX_FILES) {
            val (dir, depth) = stack.removeLast()
            if (depth > MAX_DEPTH) continue
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
            if (!seen.add(canonical)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                visited++
                if (child.isFile && child.name.lowercase(Locale.ROOT) == wanted && child.canRead()) return child
                if (visited >= MAX_FILES) break
                if (child.isDirectory && !child.isHidden) stack.add(child to depth + 1)
            }
        }
        return null
    }

    private fun isUsableResourceLocation(location: String, expectedName: String): Boolean {
        if (location.startsWith(STORAGE_TREE_PREFIX)) return false
        if (location.startsWith("content:")) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(location))
            return doc?.isFile == true && doc.name?.equals(expectedName, ignoreCase = true) == true
        }
        val file = File(location)
        return file.isFile && file.canRead() && file.name.equals(expectedName, ignoreCase = true)
    }
}
