package ac.cord.auxiliary.compose.network.relays

import ac.cord.auxiliary.compose.network.sockets.NostrIncomingMessage

data class NostrPublishResult(
    val result: NostrIncomingMessage? = null,
    val error: Throwable? = null,
    val relayUrl: String,
)
