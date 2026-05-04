package ac.cord.auxiliary.compose.network.sockets

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.exceptions.NetworkException
import ac.cord.auxiliary.compose.exceptions.NostrNoticeException

sealed class NostrIncomingMessage {

    data class EventMessage(
        val subscriptionId: String,
        val nostrEvent: NostrEvent? = null,
    ) : NostrIncomingMessage()

    data class EoseMessage(
        val subscriptionId: String,
    ) : NostrIncomingMessage()

    data class OkMessage(
        val eventId: String,
        val success: Boolean,
        val message: String? = null,
    ) : NostrIncomingMessage()

    data class NoticeMessage(
        val subscriptionId: String? = null,
        val message: String? = null,
    ) : NostrIncomingMessage()

    data class AuthMessage(
        val challenge: String,
    ) : NostrIncomingMessage()

    data class CountMessage(
        val subscriptionId: String,
        val count: Int,
    ) : NostrIncomingMessage()

    data class EventsMessage(
        val subscriptionId: String,
        val nostrEvents: List<NostrEvent> = emptyList(),
    ) : NostrIncomingMessage()

    data class NegentropyMessage(
        val subscriptionId: String,
        val negentropyMessage: String,
    ) : NostrIncomingMessage()

    data class NegentropyError(
        val subscriptionId: String,
        val negentropyReason: String,
    ) : NostrIncomingMessage()
}

fun NostrIncomingMessage?.verifyOrThrow(subscriptionId: String) {
    if (this == null) {
        throw NetworkException("No messages received.")
    }

    if (this is NostrIncomingMessage.NoticeMessage) {
        throw NostrNoticeException(
            reason = this.message,
            subscriptionId = subscriptionId,
        )
    }
}
