package net.xiondigital.anlasoz.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed interface LocalModelState {
    /** This build has no native engine (unsupported ABI). */
    data object Unavailable : LocalModelState

    data object Idle : LocalModelState

    data class Loading(val fileName: String) : LocalModelState

    data class Ready(
        val fileName: String,
        val bytes: Long,
        val contextSize: Int,
        val loadMillis: Long,
    ) : LocalModelState

    data class Failed(val fileName: String, val message: String) : LocalModelState
}

/**
 * Owns the one on-device model the process has loaded.
 *
 * Loading a 2 GB model takes a few seconds and generation can take a minute, so
 * every call runs on the IO dispatcher and is serialised: two concurrent turns
 * would fight over the same KV cache. Cancellation is cooperative — the native
 * loop checks a flag between tokens.
 */
class LocalEngine {

    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)
    private var handle = 0L
    private var loadedFile: File? = null
    private var backendReady = false

    private val _state = MutableStateFlow<LocalModelState>(
        if (LlamaBridge.isAvailable) LocalModelState.Idle else LocalModelState.Unavailable,
    )
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private val _tokens = MutableStateFlow(0)
    val tokens: StateFlow<Int> = _tokens.asStateFlow()

    val isAvailable: Boolean get() = LlamaBridge.isAvailable

    /** Text for the Settings card: what the native library reports about itself. */
    fun backendInfo(): String =
        if (!LlamaBridge.isAvailable) "Native engine not present in this build"
        else runCatching { LlamaBridge.backendInfo() }.getOrDefault("").trim()

    fun loadOnce(file: File, contextSize: Int = DEFAULT_CONTEXT) {
        if (handle != 0L && loadedFile?.absolutePath == file.absolutePath) return
        requireMemory(file)
        unload()
        _state.value = LocalModelState.Loading(file.name)
        val started = System.currentTimeMillis()
        if (!backendReady) {
            LlamaBridge.backendInit()
            backendReady = true
        }
        val threads = threadsForDevice()
        val newHandle = LlamaBridge.loadModel(file.absolutePath, contextSize, threads)
        if (newHandle == 0L) {
            _state.value = LocalModelState.Failed(file.name, "The model could not be loaded")
            throw AiException(AiFailure.NOT_CONFIGURED, "The model could not be loaded")
        }
        handle = newHandle
        loadedFile = file
        _state.value = LocalModelState.Ready(
            fileName = file.name,
            bytes = file.length(),
            contextSize = contextSize,
            loadMillis = System.currentTimeMillis() - started,
        )
    }

    /** Convenience wrapper used by the app: load, generate, stay loaded. */
    suspend fun complete(
        file: File,
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int,
        timeoutMillis: Long = GENERATION_TIMEOUT_MS,
    ): String = mutex.withLock {
        if (!LlamaBridge.isAvailable) {
            throw AiException(AiFailure.NOT_CONFIGURED, "This build has no on-device engine")
        }
        if (!file.exists()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "The selected model file is missing")
        }
        cancelled.set(false)
        _tokens.value = 0
        withContext(Dispatchers.IO) { loadOnce(file) }

        val roles = messages.map { it.role }.toTypedArray()
        val contents = messages.map { it.content }.toTypedArray()
        val current = handle
        val text = try {
            withTimeoutOrNull(timeoutMillis) {
                withContext(Dispatchers.IO) {
                    LlamaBridge.generate(
                        handle = current,
                        roles = roles,
                        contents = contents,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = 0.95f,
                        progress = LlamaBridge.TokenProgress { count -> _tokens.value = count },
                    )
                }
            }
        } finally {
            // The counter is for the live status line, not a running total.
            _tokens.value = 0
        }
        if (cancelled.get()) throw AiException(AiFailure.CANCELLED, "Generation stopped")
        if (text == null) {
            throw AiException(AiFailure.TIMEOUT, "The phone did not finish in time")
        }
        if (text.isBlank()) {
            throw AiException(AiFailure.MALFORMED, "The model returned nothing")
        }
        text
    }

    fun cancel() {
        cancelled.set(true)
        val current = handle
        if (current != 0L) runCatching { LlamaBridge.cancel(current) }
    }

    fun unload() {
        val current = handle
        handle = 0L
        loadedFile = null
        if (current != 0L) runCatching { LlamaBridge.freeModel(current) }
        if (LlamaBridge.isAvailable) _state.value = LocalModelState.Idle
    }

    /** Loads a model ahead of the first message, for the "Load now" button. */
    suspend fun preload(file: File, contextSize: Int = DEFAULT_CONTEXT) {
        if (!LlamaBridge.isAvailable) {
            throw AiException(AiFailure.NOT_CONFIGURED, "This build has no on-device engine")
        }
        if (!file.exists()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "The selected model file is missing")
        }
        mutex.withLock {
            withContext(Dispatchers.IO) { loadOnce(file, contextSize) }
        }
    }

    /**
     * ggml aborts the whole process when a buffer allocation fails, which looks
     * like a crash with no explanation. Refusing early with a readable message
     * is the only way to keep that from happening on a busy phone.
     */
    private fun requireMemory(file: File) {
        val available = availableMemoryBytes()
        if (available <= 0) return
        val needed = file.length() + MIN_HEADROOM_BYTES
        if (needed > available) {
            throw AiException(
                AiFailure.MEMORY,
                "This model needs about ${needed / 1_000_000} MB of free memory and only " +
                    "${available / 1_000_000} MB is available.",
            )
        }
    }

    private fun availableMemoryBytes(): Long = runCatching {
        File("/proc/meminfo").readLines()
            .firstOrNull { it.startsWith("MemAvailable:") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.toLong()
            ?.times(1024)
    }.getOrNull() ?: 0L

    private fun threadsForDevice(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return cores.coerceIn(2, 6)
    }

    companion object {
        const val DEFAULT_CONTEXT = 4096

        /** KV cache, compute buffers and the Android runtime all need room too. */
        private const val MIN_HEADROOM_BYTES = 350L * 1024 * 1024
        private const val GENERATION_TIMEOUT_MS = 8 * 60 * 1000L
    }
}
