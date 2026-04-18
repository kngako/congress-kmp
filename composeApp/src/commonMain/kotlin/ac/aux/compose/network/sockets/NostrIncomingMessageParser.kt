package ac.aux.compose.network.sockets

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.network.serialization.SocketsJson
import ac.aux.compose.network.serialization.decodeFromStringOrNull
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun String.parseIncomingMessage(): NostrIncomingMessage? {
    val jsonArray = SocketsJson.decodeFromStringOrNull<JsonArray>(this)
    val verbElement = jsonArray?.elementAtOrNull(0) ?: return null

    return try {
        when (verbElement.toIncomingMessageType()) {
            NostrVerb.Incoming.EVENT -> jsonArray.takeAsEventIncomingMessage()
            NostrVerb.Incoming.EOSE -> jsonArray.takeAsEoseIncomingMessage()
            NostrVerb.Incoming.OK -> jsonArray.takeAsOkIncomingMessage()
            NostrVerb.Incoming.NOTICE -> jsonArray.takeAsNoticeIncomingMessage()
            NostrVerb.Incoming.AUTH -> jsonArray.takeAsAuthIncomingMessage()
            NostrVerb.Incoming.COUNT -> jsonArray.takeAsCountIncomingMessage()
            NostrVerb.Incoming.EVENTS -> jsonArray.takeAsEventsIncomingMessage()
        }
    } catch (error: Exception) {
        Logger.withTag("String.parseIncomingMessage").w(error) { "Unable to parse incoming message." }
        null
    }
}

private fun JsonArray.takeAsAuthIncomingMessage(): NostrIncomingMessage? {
    val challenge = elementAtOrNull(1) ?: return null
    return NostrIncomingMessage.AuthMessage(
        challenge = challenge.jsonPrimitive.content,
    )
}

private fun JsonArray.takeAsCountIncomingMessage(): NostrIncomingMessage? {
    val subscriptionId = elementAtOrNull(1)?.toSubscriptionId()
    val count = elementAtOrNull(2)
        ?.jsonObject
        ?.get("count")
        ?.jsonPrimitive?.intOrNull

    return if (subscriptionId != null && count != null) {
        NostrIncomingMessage.CountMessage(
            subscriptionId = subscriptionId,
            count = count,
        )
    } else {
        null
    }
}

private fun JsonArray.takeAsEoseIncomingMessage(): NostrIncomingMessage? {
    val subscriptionElement = elementAtOrNull(1) ?: return null
    return NostrIncomingMessage.EoseMessage(
        subscriptionId = subscriptionElement.toSubscriptionId(),
    )
}

private fun JsonArray.takeAsEventIncomingMessage(): NostrIncomingMessage? {
    val subscriptionId = elementAtOrNull(1)?.toSubscriptionId()
    val event = elementAtOrNull(2)?.jsonObject
    val kind = event?.getMessageNostrEventKind()

    if (subscriptionId == null || kind == null) return null

    val nostrEvent = NostrEvent.fromEvent(
        Event.fromJson(
            event.toString()
        )
    )

    return NostrIncomingMessage.EventMessage(
        subscriptionId = subscriptionId,
        nostrEvent = nostrEvent,
    )
}

private fun JsonArray.takeAsEventsIncomingMessage(): NostrIncomingMessage? {
    val subscriptionId = elementAtOrNull(1)?.toSubscriptionId()
    val events = elementAtOrNull(2)?.jsonArray

    if (subscriptionId == null || events == null) return null

    val nostrEvents = mutableListOf<NostrEvent>()

    events.map { it.jsonObject }.forEach { jsonEvent ->
        NostrEvent.fromEvent(
            Event.fromJson(
                jsonEvent.toString()
            )
        )?.let { nostrEvent ->
            nostrEvents.add(nostrEvent)
        }
    }

    return NostrIncomingMessage.EventsMessage(
        subscriptionId = subscriptionId,
        nostrEvents = nostrEvents,
    )
}

private fun JsonObject.getMessageNostrEventKind(): Kind {
    val kind = this["kind"]?.jsonPrimitive?.intOrNull
    return kind ?: -1
}

private fun JsonArray.takeAsNoticeIncomingMessage(): NostrIncomingMessage {
    val subscriptionId = elementAtOrNull(1)?.toSubscriptionId()
    val messageText = elementAtOrNull(2)?.jsonPrimitive?.content
    return NostrIncomingMessage.NoticeMessage(subscriptionId = subscriptionId, message = messageText)
}

private fun JsonArray.takeAsOkIncomingMessage(): NostrIncomingMessage? {
    val eventId = elementAtOrNull(1)?.jsonPrimitive?.content
    val success = elementAtOrNull(2)?.jsonPrimitive?.booleanOrNull
    val message = elementAtOrNull(3)?.jsonPrimitive?.content

    return if (eventId != null && success != null) {
        NostrIncomingMessage.OkMessage(
            eventId = eventId,
            success = success,
            message = message,
        )
    } else {
        null
    }
}

private fun JsonElement.toIncomingMessageType(): NostrVerb.Incoming {
    return when (this.jsonPrimitive.content) {
        "EVENT" -> NostrVerb.Incoming.EVENT
        "EOSE" -> NostrVerb.Incoming.EOSE
        "OK" -> NostrVerb.Incoming.OK
        "AUTH" -> NostrVerb.Incoming.AUTH
        "COUNT" -> NostrVerb.Incoming.COUNT
        "EVENTS" -> NostrVerb.Incoming.EVENTS
        else -> NostrVerb.Incoming.NOTICE
    }
}

private fun JsonElement.toSubscriptionId(): String = this.jsonPrimitive.content
