package press.mantra.compose.network.relays

import press.mantra.compose.network.sockets.NostrIncomingMessage

data class NostrPublishResult(
    val result: NostrIncomingMessage? = null,
    val error: Throwable? = null,
    val relayUrl: String,
)
