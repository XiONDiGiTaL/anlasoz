package net.xiondigital.anlasoz.ai

/**
 * Thin, explicit surface over the llama.cpp JNI library.
 *
 * Nothing here decides policy: it loads one model, generates once, and reports
 * cancellation. The lifecycle lives in [LocalEngine].
 *
 * The library is arm64 only. On an unsupported build [isAvailable] is false and
 * the rest of the app keeps working with a remote provider.
 */
object LlamaBridge {

    private val loaded: Boolean = runCatching { System.loadLibrary("anlasoz_llm") }.isSuccess

    val isAvailable: Boolean get() = loaded

    fun interface TokenProgress {
        fun onProgress(tokens: Int)
    }

    external fun backendInit()

    external fun backendInfo(): String

    /** True when this build carries a GPU backend (Vulkan); CPU-only builds say false. */
    external fun supportsGpuOffload(): Boolean

    /** Hardware name of the GPU backend, or "" when there is none. */
    external fun gpuDeviceName(): String

    /**
     * @param gpuLayers 0 keeps everything on the CPU, a negative count asks for
     *   every layer the backend will accept.
     */
    external fun loadModel(path: String, nCtx: Int, nThreads: Int, gpuLayers: Int): Long

    external fun modelLayerCount(handle: Long): Int

    external fun freeModel(handle: Long)

    external fun cancel(handle: Long)

    external fun generate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        progress: TokenProgress?,
    ): String?
}
