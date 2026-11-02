package net.xiondigital.anlasoz.local

/**
 * Models the app can fetch for on-device use.
 *
 * Only small chat models belong here: the analysis prompt asks for a long
 * structured answer, and a phone has a few gigabytes of usable memory at most.
 * Sizes and hashes are the publisher's own figures; when they are known the
 * download is verified against them before the file is offered to the engine.
 */
data class LocalModelSource(
    val id: String,
    val name: String,
    val publisher: String,
    val note: String,
    val fileName: String,
    val url: String,
    val bytes: Long? = null,
    val sha256: String? = null,
    val recommended: Boolean = false,
)

object ModelCatalog {

    val vexiumCoreLight = LocalModelSource(
        id = "vexium-core-1.5b",
        name = "Vexium Core Light 1.5B",
        publisher = "XiØN · Qwen2.5-1.5B-Instruct",
        note = "The default. Around 90 seconds per analysis on an 8 GB phone.",
        fileName = "vexium-core-1.5b-q4_k_m.gguf",
        url = "https://github.com/XiONDiGiTaL/Vexium-Core/releases/download/v1/vexium-core-1.5b-q4_k_m.gguf",
        bytes = 1_117_320_736L,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        recommended = true,
    )

    val vexiumCore3B = LocalModelSource(
        id = "vexium-core-3b",
        name = "Vexium Core 3B",
        publisher = "XiØN · Qwen2.5-3B-Instruct",
        note = "Better explanations, but it needs a 12 GB phone to stay quick. On 8 GB the app may refuse it.",
        fileName = "vexium-core-3b-q4_k_m.gguf",
        url = "https://github.com/XiONDiGiTaL/Vexium-Core/releases/download/v2/vexium-core-3b-q4_k_m.gguf",
        bytes = 2_104_932_768L,
        sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
    )

    val qwenHalfB = LocalModelSource(
        id = "qwen2.5-0.5b",
        name = "Qwen2.5 0.5B (test model)",
        publisher = "Alibaba Cloud",
        note = "Tiny sanity check for the engine. Too small to trust as a tutor.",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
    )

    val downloadable: List<LocalModelSource> = listOf(vexiumCoreLight, vexiumCore3B, qwenHalfB)

    /** Where the Settings card sends people who want something else. */
    const val HUGGING_FACE_GGUF_BROWSE =
        "https://huggingface.co/models?library=gguf&sort=trending&search=instruct"

    fun byId(id: String): LocalModelSource? = downloadable.firstOrNull { it.id == id }

    fun byFileName(fileName: String): LocalModelSource? =
        downloadable.firstOrNull { it.fileName == fileName }

    /** Human-readable size for cards and lists. */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "unknown size"
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) return String.format(java.util.Locale.US, "%.2f GB", gb)
        return String.format(java.util.Locale.US, "%.0f MB", bytes / 1_000_000.0)
    }
}
