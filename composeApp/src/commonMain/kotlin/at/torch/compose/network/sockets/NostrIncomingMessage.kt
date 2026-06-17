package at.torch.compose.network.sockets

import at.torch.compose.exceptions.NetworkException
import at.torch.compose.exceptions.NostrNoticeException

sealed class NostrIncomingMessage {

    data class EventMessage(
        val subscriptionId: String,
        val nostrEvent: at.torch.compose.database.model.NostrEvent? = null,
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
        val nostrEvents: List<at.torch.compose.database.model.NostrEvent> = emptyList(),
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
        throw _root_ide_package_.at.torch.compose.exceptions.NetworkException("No messages received.")
    }

    if (this is NostrIncomingMessage.NoticeMessage) {
        throw _root_ide_package_.at.torch.compose.exceptions.NostrNoticeException(
            reason = this.message,
            subscriptionId = subscriptionId,
        )
    }
}
