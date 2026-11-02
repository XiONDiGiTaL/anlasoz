package net.xiondigital.anlasoz.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatMessage(val role: String, val content: String)

/**
 * Minimal, provider-agnostic chat-completions client.
 *
 * Anything that speaks the OpenAI chat-completions shape works: the app only
 * needs a base URL, a model name and a key the user owns.
 */
class AiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val parsingJson: Json get() = json

    suspend fun complete(
        baseUrl: String,
        model: String,
        apiKey: String,
        messages: List<ChatMessage>,
        temperature: Float = 0.35f,
        jsonMode: Boolean = true,
        maxTokens: Int = 2200,
    ): String {
        if (apiKey.isBlank()) throw AiException(AiFailure.NO_KEY, "Missing API key")
        if (baseUrl.isBlank() || model.isBlank()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "Missing base URL or model")
        }
        val endpoint = ProviderPresets.chatEndpoint(baseUrl)
        if (endpoint.isEmpty()) throw AiException(AiFailure.NOT_CONFIGURED, "Invalid base URL")

        var useJsonMode = jsonMode
        var attempt = 0
        var lastError: AiException? = null
        while (attempt < 3) {
            val request = buildRequest(endpoint, model, apiKey, messages, temperature, useJsonMode, maxTokens)
            try {
                val body = execute(request)
                return extractContent(body)
            } catch (e: AiException) {
                lastError = e
                when (e.failure) {
                    AiFailure.RATE_LIMIT, AiFailure.SERVER, AiFailure.NETWORK, AiFailure.TIMEOUT -> {
                        attempt++
                        if (attempt >= 3) break
                        delay(700L * attempt)
                    }
                    else -> {
                        // Some endpoints reject response_format; retry once without it.
                        if (useJsonMode && e.failure == AiFailure.NOT_CONFIGURED) {
                            useJsonMode = false
                            attempt++
                            continue
                        }
                        break
                    }
                }
            }
        }
        throw lastError ?: AiException(AiFailure.UNKNOWN, "Request failed")
    }

    /** Cheap round trip used by Settings to verify URL, key and model together. */
    suspend fun testConnection(
        baseUrl: String,
        model: String,
        apiKey: String,
    ): String {
        val messages = listOf(
            ChatMessage("system", Prompts.TEST_SYSTEM),
            ChatMessage("user", "ping"),
        )
        val raw = complete(
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            messages = messages,
            temperature = 0f,
            jsonMode = false,
            maxTokens = 40,
        )
        return raw.trim().take(120)
    }

    private fun buildRequest(
        endpoint: String,
        model: String,
        apiKey: String,
        messages: List<ChatMessage>,
        temperature: Float,
        jsonMode: Boolean,
        maxTokens: Int,
    ): Request {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
            if (jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
        }
        return Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(mapTransportError(e))
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use { resp ->
                        val text = runCatching { resp.body?.string().orEmpty() }.getOrElse { "" }
                        if (resp.isSuccessful) {
                            if (cont.isActive) cont.resume(text)
                        } else if (cont.isActive) {
                            cont.resumeWithException(mapHttpError(resp.code, text))
                        }
                    }
                }
            },
        )
    }

    private fun mapTransportError(e: IOException): AiException = when (e) {
        is SocketTimeoutException -> AiException(AiFailure.TIMEOUT, "Request timed out", e)
        is UnknownHostException -> AiException(AiFailure.NETWORK, "Host not reachable", e)
        is ConnectException -> AiException(AiFailure.NETWORK, "Connection refused", e)
        else -> AiException(
            AiFailure.NETWORK,
            e.message ?: "Network error",
            e,
        )
    }

    private fun mapHttpError(code: Int, body: String): AiException {
        val providerMessage = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]
            when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
        }.getOrNull()
        val detail = providerMessage?.take(240) ?: body.take(160).ifBlank { "HTTP $code" }
        val failure = when (code) {
            401, 403 -> AiFailure.UNAUTHORIZED
            402 -> AiFailure.UNAUTHORIZED
            404 -> AiFailure.NOT_CONFIGURED
            408 -> AiFailure.TIMEOUT
            429 -> AiFailure.RATE_LIMIT
            in 500..599 -> AiFailure.SERVER
            in 400..499 -> AiFailure.NOT_CONFIGURED
            else -> AiFailure.UNKNOWN
        }
        return AiException(failure, "HTTP $code: $detail")
    }

    /** Pulls the assistant text out of the provider envelope. */
    private fun extractContent(body: String): String {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AiException(AiFailure.MALFORMED, "Provider returned non-JSON")
        root["error"]?.let { error ->
            val message = when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
            throw AiException(AiFailure.SERVER, message ?: "Provider error")
        }
        val choices = root["choices"] as? JsonArray
            ?: throw AiException(AiFailure.MALFORMED, "No choices in provider reply")
        val message = choices.firstOrNull()?.jsonObject?.get("message") as? JsonObject
            ?: throw AiException(AiFailure.MALFORMED, "No message in provider reply")
        val content = message["content"]
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.joinToString("") { part ->
                val obj = part as? JsonObject ?: return@joinToString ""
                obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            else -> ""
        }
        if (text.isBlank()) {
            val refusal = message["refusal"]?.jsonPrimitive?.contentOrNull
            throw AiException(
                AiFailure.EMPTY,
                refusal?.take(200) ?: "The model returned an empty reply",
            )
        }
        return text
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
