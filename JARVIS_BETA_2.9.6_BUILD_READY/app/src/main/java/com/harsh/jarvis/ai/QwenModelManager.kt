package com.harsh.jarvis.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.codeshipping.llamakotlin.LlamaModel

/** Qwen3 GGUF manager. ResourceLocator finds, validates and remembers the user's model location. */
class QwenModelManager(private val context: Context) : AutoCloseable {
    companion object {
        const val MODEL_FILE_NAME = "Qwen3-1.7B-Q4_K_M.gguf"
        private const val INTERNAL_NAME = "qwen3-1.7b-q4_k_m.gguf"
        private const val MAX_MODEL_BYTES = 3L * 1024L * 1024L * 1024L
        private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        private const val FIXED_RUNTIME_OVERHEAD_BYTES = 512L * 1024L * 1024L
        private const val MIN_RUNTIME_RAM_BYTES = 5L * 1024L * 1024L * 1024L
    }

    private val locator = ResourceLocator(context)
    private val spec = ResourceLocator.ResourceSpec(
        key = "qwen3_1_7b_q4_k_m",
        fileName = MODEL_FILE_NAME,
        priority = ResourceLocator.Priority.DAILY,
        preferredPaths = emptyList()
    )

    private var model: LlamaModel? = null
    private var loadedPath: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generationMutex = Mutex()
    private val lifecycleLock = Any()
    private val _status = MutableStateFlow("Qwen3 model status is being checked…")
    val status: StateFlow<String> = _status.asStateFlow()
    @Volatile private var lastError: String? = null
    @Volatile private var closed = false

    init { refreshStatusInBackground() }

    fun preferredPath(): String = "Use Import Model or choose a folder containing $MODEL_FILE_NAME"
    fun rememberedLocation(): String? = locator.remembered(spec)
    fun resolvedLocation(): String? = locator.remembered(spec)
    fun isModelAvailable(): Boolean = locator.remembered(spec) != null
    fun isLoaded(): Boolean = model?.isLoaded == true

    fun status(): String = _status.value

    fun lastError(): String? = lastError

    private fun refreshStatusInBackground() {
        scope.launch {
            val found = runCatching { locator.resolve(spec) }.getOrNull()
            _status.value = when {
                isLoaded() -> "Qwen3 1.7B loaded"
                found != null -> "Qwen3 model found"
                lastError != null -> "Qwen3 unavailable: ${lastError}"
                else -> "Qwen3 model not found"
            }
        }
    }

    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        importFromUriInternal(uri)
            ?: throw IllegalArgumentException("JARVIS could not import the selected model file.")
    }

    private fun validateModelFile(file: File) {
        require(file.isFile && file.canRead()) { "The Qwen3 model file cannot be read." }
        require(file.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
            "The selected GGUF has an invalid size (${file.length()} bytes)."
        }
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            require(input.readNBytes(4).contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                "The selected file is not a valid GGUF model."
            }
        }
    }

    private suspend fun importFromUriInternal(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = android.provider.OpenableColumns.DISPLAY_NAME
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(name), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        require(displayName?.equals(MODEL_FILE_NAME, ignoreCase = true) == true) {
            "Select $MODEL_FILE_NAME."
        }

        val target = File(context.filesDir, "models/$INTERNAL_NAME")
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$INTERNAL_NAME.part")
        temp.delete()

        val sizeHint = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        require(sizeHint <= 0L || sizeHint in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
            "The selected model is outside JARVIS's safe 100 MB–3 GB size range."
        }
        require(hasEnoughStorage(sizeHint.coerceAtLeast(0L))) {
            "There is not enough internal storage to import the Qwen3 model safely."
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_MODEL_BYTES) { "The selected model is larger than the 3 GB safety limit." }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: return@withContext null
            validateModelFile(temp)
            if (!temp.renameTo(target)) throw IllegalStateException("JARVIS could not finalize the imported model file.")
            locator.remember(spec, target.absolutePath)
            _status.value = "Qwen3 model found"
            target.absolutePath
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun hasEnoughStorage(bytesToCopy: Long): Boolean {
        if (bytesToCopy <= 0) return true
        val available = context.filesDir.usableSpace
        return available >= bytesToCopy + FIXED_RUNTIME_OVERHEAD_BYTES
    }

    suspend fun rememberModelFolder(uri: Uri): String = withContext(Dispatchers.IO) {
        if (!locator.addGrantedTree(uri)) throw SecurityException("JARVIS could not retain access to that folder.")
        (locator.resolve(spec)?.location
            ?: throw IllegalArgumentException("$MODEL_FILE_NAME was not found in the selected folder."))
            .also { _status.value = "Qwen3 model found" }
    }

    private suspend fun resolveModelPath(): String? = withContext(Dispatchers.IO) {
        val found = locator.resolve(spec) ?: return@withContext null
        if (!found.isUri) {
            val file = File(found.location)
            validateModelFile(file)
            return@withContext file.absolutePath
        }

        // SAF-backed files do not expose a stable filesystem path. Import the discovered file
        // privately once, then remember the private copy for fast future loads.
        importFromUriInternal(Uri.parse(found.location))
    }

    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = resolveModelPath() ?: return@withContext false
            if (model?.isLoaded == true && loadedPath == path) return@withContext true

            model?.close()
            model = null
            loadedPath = null

            val memoryInfo = ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                activityManager.getMemoryInfo(memoryInfo)
                val fileSize = File(path).length()
                val required = maxOf(MIN_RUNTIME_RAM_BYTES, (fileSize * 3L) / 2L + FIXED_RUNTIME_OVERHEAD_BYTES)
                if (memoryInfo.availMem < required) {
                    throw IllegalStateException("Not enough available RAM to load Qwen3 safely. JARVIS estimates about ${required / (1024 * 1024)} MB of free RAM is needed (5 GB minimum).")
                }
            }

            model = LlamaModel.load(path) {
                contextSize = 2048
                batchSize = 256
                threads = 4
                threadsBatch = 4
                temperature = 0.65f
                topP = 0.9f
                topK = 40
                repeatPenalty = 1.08f
                maxTokens = 384
                useMmap = true
                useMlock = false
                gpuLayers = 0
            }
            loadedPath = path
            lastError = null
            _status.value = "Qwen3 1.7B loaded"
            true
        } catch (t: Throwable) {
            model?.close()
            model = null
            loadedPath = null
            lastError = t.message?.take(300) ?: t::class.java.simpleName
            _status.value = "Qwen3 unavailable: ${lastError}"
            false
        }
    }

    suspend fun generate(systemPrompt: String, userPrompt: String): String = generationMutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) return@withContext "JARVIS local Qwen brain is unavailable because the model manager is closed."
            if (!load()) return@withContext "Qwen3 could not be loaded. Check that $MODEL_FILE_NAME is present, valid, and that enough storage/RAM is available."
            val prompt = formatQwen3Chat(systemPrompt, userPrompt)
            runCatching { model?.generate(prompt)?.let(::cleanQwenOutput).orEmpty() }
                .getOrElse { "Qwen3 generation failed safely: ${it.message ?: "native inference error"}" }
                .ifBlank { "Qwen3 returned an empty response." }
        }
    }

    private fun formatQwen3Chat(system: String, user: String): String = buildString {
        append("<|im_start|>system\n")
        append(system.trim())
        append("\n<|im_end|>\n")
        append("<|im_start|>user\n")
        append("/no_think\n")
        append(user.trim())
        append("\n<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    private fun cleanQwenOutput(value: String): String = value
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
        .trim()

    override fun close() {
        if (closed) return
        closed = true
        scope.launch {
            generationMutex.withLock {
                synchronized(lifecycleLock) {
                    model?.close()
                    model = null
                    loadedPath = null
                }
            }
        }
    }
}
