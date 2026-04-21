package ac.cord.auxiliary.compose.network.serialization

import ac.cord.auxiliary.compose.database.model.NostrEvent
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal val SocketsJson = CommonJson


fun JsonObject?.asNostrEventOrNull(): NostrEvent? {
    return try {
        if (this != null) SocketsJson.decodeFromJsonElement(this) else null
    } catch (error: IllegalArgumentException) {
        Logger.withTag("JsonObject?.asNostrEventOrNull").w(error) { "Unable to map as NostrEvent." }
        this?.let(SocketsJson::encodeToString)?.let { Logger.withTag("JsonObject?.asNostrEventOrNull").w { it } }
        null
    }
}
