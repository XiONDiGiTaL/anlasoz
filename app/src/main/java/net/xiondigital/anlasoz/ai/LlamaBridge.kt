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

    external fun loadModel(path: String, nCtx: Int, nThreads: Int): Long

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
