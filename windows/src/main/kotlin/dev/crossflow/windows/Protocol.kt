package dev.crossflow.windows

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ClipMessage(
    val type: String = "clipboard",
    val content: String,
    val source: String
)

object Protocol {
    const val PORT         = 35647
    const val SERVICE_TYPE = "_crossflow._tcp."

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(msg: ClipMessage): String = json.encodeToString(msg) + "\n"

    fun decode(line: String): ClipMessage? = runCatching {
        json.decodeFromString<ClipMessage>(line.trim())
    }.getOrNull()
}
