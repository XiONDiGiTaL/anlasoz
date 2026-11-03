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
        /** Weights were handed to the GPU backend rather than kept on the CPU. */
        val offloaded: Boolean = false,
        /** GPU name when offloaded, e.g. "Adreno (TM) 740". */
        val device: String = "",
        val layers: Int = 0,
        /**
         * The GPU driver refused this model's shaders, so it was reloaded on the
         * CPU. Phone drivers do reject specific quantised matmul shaders; the
         * app says so instead of pretending the GPU is in use.
         */
        val gpuRejected: Boolean = false,
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
    private var loadedOffload = false
    private var backendReady = false

    /** Sticky until the user changes model or offload setting: no point retrying. */
    @Volatile
    private var gpuRejected = false

    /**
     * Device enumeration is a JNI call that wakes the backend, so ask once.
     * A build can carry the Vulkan backend and still find no usable driver,
     * hence the name check: no named device means no offload on offer.
     */
    private val gpuName: String by lazy {
        if (!LlamaBridge.isAvailable) "" else runCatching { LlamaBridge.gpuDeviceName() }.getOrDefault("")
    }
    private val gpuByDevice: Boolean by lazy {
        LlamaBridge.isAvailable &&
            runCatching { LlamaBridge.supportsGpuOffload() }.getOrDefault(false) &&
            gpuName.isNotBlank()
    }

    private val _state = MutableStateFlow<LocalModelState>(
        if (LlamaBridge.isAvailable) LocalModelState.Idle else LocalModelState.Unavailable,
    )
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private val _tokens = MutableStateFlow(0)
    val tokens: StateFlow<Int> = _tokens.asStateFlow()

    val isAvailable: Boolean get() = LlamaBridge.isAvailable

    /** This build can hand layers to a GPU, and one was found. */
    val gpuAvailable: Boolean get() = gpuByDevice

    val gpuDeviceName: String get() = gpuName

    /** Text for the Settings card: what the native library reports about itself. */
    fun backendInfo(): String =
        if (!LlamaBridge.isAvailable) "Native engine not present in this build"
        else runCatching { LlamaBridge.backendInfo() }.getOrDefault("").trim()

    fun loadOnce(
        file: File,
        contextSize: Int = DEFAULT_CONTEXT,
        offload: Boolean = false,
    ) {
        val useGpu = offload && gpuByDevice && !gpuRejected
        if (handle != 0L && loadedFile?.absolutePath == file.absolutePath && loadedOffload == useGpu) return
        requireMemory(file)
        unload()
        _state.value = LocalModelState.Loading(file.name)
        val started = System.currentTimeMillis()
        if (!backendReady) {
            LlamaBridge.backendInit()
            backendReady = true
        }
        val threads = threadsForDevice()
        val newHandle = LlamaBridge.loadModel(
            path = file.absolutePath,
            nCtx = contextSize,
            nThreads = threads,
            gpuLayers = if (useGpu) ALL_LAYERS else 0,
        )
        if (newHandle == 0L) {
            _state.value = LocalModelState.Failed(file.name, "The model could not be loaded")
            throw AiException(AiFailure.NOT_CONFIGURED, "The model could not be loaded")
        }
        handle = newHandle
        loadedFile = file
        loadedOffload = useGpu
        _state.value = LocalModelState.Ready(
            fileName = file.name,
            bytes = file.length(),
            contextSize = contextSize,
            loadMillis = System.currentTimeMillis() - started,
            offloaded = useGpu,
            device = if (useGpu) gpuName else "",
            layers = runCatching { LlamaBridge.modelLayerCount(newHandle) }.getOrDefault(0),
            gpuRejected = gpuRejected,
        )
    }

    /** Convenience wrapper used by the app: load, generate, stay loaded. */
    suspend fun complete(
        file: File,
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int,
        offload: Boolean = false,
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
        withContext(Dispatchers.IO) { loadOnce(file, offload = offload) }

        val text = try {
            generate(messages, maxTokens, temperature, timeoutMillis)
        } catch (e: IllegalStateException) {
            if (!isGpuRejection(e) || !offload || gpuRejected) throw e
            // The driver would not compile this model's shaders. Reload on the
            // CPU and answer the message anyway; the card reports the fallback.
            gpuRejected = true
            withContext(Dispatchers.IO) { loadOnce(file, offload = false) }
            generate(messages, maxTokens, temperature, timeoutMillis)
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

    private suspend fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Float,
        timeoutMillis: Long,
    ): String? {
        val roles = messages.map { it.role }.toTypedArray()
        val contents = messages.map { it.content }.toTypedArray()
        val current = handle
        return try {
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
    }

    private fun isGpuRejection(e: IllegalStateException): Boolean =
        e.message.orEmpty().startsWith(GPU_REJECTED)

    /** Lets a fresh attempt happen after the user changes the setting or the model. */
    fun clearGpuRejection() {
        gpuRejected = false
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
        loadedOffload = false
        if (current != 0L) runCatching { LlamaBridge.freeModel(current) }
        if (LlamaBridge.isAvailable) _state.value = LocalModelState.Idle
    }

    /** Loads a model ahead of the first message, for the "Load now" button. */
    suspend fun preload(
        file: File,
        contextSize: Int = DEFAULT_CONTEXT,
        offload: Boolean = false,
    ) {
        if (!LlamaBridge.isAvailable) {
            throw AiException(AiFailure.NOT_CONFIGURED, "This build has no on-device engine")
        }
        if (!file.exists()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "The selected model file is missing")
        }
        mutex.withLock {
            withContext(Dispatchers.IO) { loadOnce(file, contextSize, offload) }
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

    /**
     * Threads are a barrier: every layer waits for the slowest one, so the little
     * cores are not worth counting. On a Snapdragon 8 Gen 2 the three 2.0 GHz
     * cores sit at 60% of the prime core's clock and pull every quantised matmul
     * down with them; the five 2.8-3.4 GHz cores are the ones that pay.
     */
    private fun threadsForDevice(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val clocks = (0 until cores).map { core ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrDefault(0L)
        }
        val fastest = clocks.maxOrNull() ?: 0L
        val big = if (fastest > 0) clocks.count { it > 0 && it * 100 >= fastest * 80 } else 0
        return if (big in 2..cores) big else cores.coerceAtMost(4)
    }

    companion object {
        const val DEFAULT_CONTEXT = 4096

        /** Must match GPU_REJECTED in llama_jni.cpp. */
        private const val GPU_REJECTED = "GPU_UNAVAILABLE"

        /** llama.cpp treats a negative layer count as "all of them". */
        private const val ALL_LAYERS = -1

        /** KV cache, compute buffers and the Android runtime all need room too. */
        private const val MIN_HEADROOM_BYTES = 350L * 1024 * 1024
        private const val GENERATION_TIMEOUT_MS = 8 * 60 * 1000L
    }
}
