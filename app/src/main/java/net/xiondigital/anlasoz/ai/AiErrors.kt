package net.xiondigital.anlasoz.ai

enum class AiFailure {
    NO_KEY,
    NOT_CONFIGURED,
    NETWORK,
    TIMEOUT,
    UNAUTHORIZED,
    RATE_LIMIT,
    SERVER,
    MALFORMED,
    EMPTY,
    CANCELLED,
    UNKNOWN,
}

class AiException(
    val failure: AiFailure,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Short, non-technical text for the UI. */
val AiFailure.headline: String
    get() = when (this) {
        AiFailure.NO_KEY -> "No API key yet"
        AiFailure.NOT_CONFIGURED -> "Finish the model setup"
        AiFailure.NETWORK -> "No connection"
        AiFailure.TIMEOUT -> "The model took too long"
        AiFailure.UNAUTHORIZED -> "The key was rejected"
        AiFailure.RATE_LIMIT -> "Rate limited"
        AiFailure.SERVER -> "The provider had a problem"
        AiFailure.MALFORMED -> "Unreadable model reply"
        AiFailure.EMPTY -> "Nothing to analyze"
        AiFailure.CANCELLED -> "Cancelled"
        AiFailure.UNKNOWN -> "Something went wrong"
    }

val AiFailure.detail: String
    get() = when (this) {
        AiFailure.NO_KEY -> "Add your provider key in Settings. It is stored encrypted on this device and never leaves it except to call your chosen endpoint."
        AiFailure.NOT_CONFIGURED -> "Set a base URL and model name in Settings, then test the connection."
        AiFailure.NETWORK -> "Check your connection and try again. Saved translations stay available offline."
        AiFailure.TIMEOUT -> "The request timed out. Try again, or pick a faster model."
        AiFailure.UNAUTHORIZED -> "Check the key in Settings. Some providers also need the model name to match your plan."
        AiFailure.RATE_LIMIT -> "Too many requests. Wait a moment and retry."
        AiFailure.SERVER -> "The provider returned an error. Retry in a moment."
        AiFailure.MALFORMED -> "The model did not return the expected JSON. Retry, or switch to a model with reliable JSON mode."
        AiFailure.EMPTY -> "Type or paste a message first."
        AiFailure.CANCELLED -> "The request was cancelled."
        AiFailure.UNKNOWN -> "Try again."
    }
