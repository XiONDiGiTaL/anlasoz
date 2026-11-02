package net.xiondigital.anlasoz.ai

/**
 * AnlaSöz talks to any OpenAI-compatible chat-completions endpoint. The app
 * ships presets as conveniences only; nothing ties the code to one vendor.
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val hint: String,
)

object ProviderPresets {

    val freebuff = ProviderPreset(
        id = "freebuff",
        name = "Freebuff API",
        baseUrl = "https://freebuff.com/api/v1/paid",
        model = "",
        hint = "Your Freebuff developer key. Enter the model name shown for your plan.",
    )

    val openai = ProviderPreset(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
        hint = "A key from platform.openai.com.",
    )

    val openrouter = ProviderPreset(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        model = "openai/gpt-4o-mini",
        hint = "One key for many models; model names look like vendor/model.",
    )

    val zai = ProviderPreset(
        id = "zai",
        name = "Z.ai",
        baseUrl = "https://api.z.ai/api/paas/v4",
        model = "glm-4-flash",
        hint = "GLM models over an OpenAI-compatible endpoint.",
    )

    /** Runs a GGUF file in this process through llama.cpp — no key, no network. */
    val device = ProviderPreset(
        id = "device",
        name = "On device",
        baseUrl = "",
        model = "",
        hint = "A GGUF model loaded on this phone. Nothing leaves the device and no key is needed.",
    )

    val local = ProviderPreset(
        id = "local",
        name = "Local server",
        baseUrl = "http://192.168.1.10:1234/v1",
        model = "local-model",
        hint = "LM Studio, Ollama or llama.cpp on your network. Plain HTTP is allowed.",
    )

    val custom = ProviderPreset(
        id = "custom",
        name = "Custom",
        baseUrl = "",
        model = "",
        hint = "Any OpenAI-compatible base URL, including your own proxy.",
    )

    val all: List<ProviderPreset> =
        listOf(freebuff, openai, openrouter, zai, device, local, custom)

    fun byId(id: String?): ProviderPreset = all.firstOrNull { it.id == id } ?: freebuff

    /** Accepts a base URL or a full endpoint and returns the chat-completions URL. */
    fun chatEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}
