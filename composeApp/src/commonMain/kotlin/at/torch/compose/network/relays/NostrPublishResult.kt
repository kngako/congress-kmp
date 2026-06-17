package at.torch.compose.network.relays

import at.torch.compose.network.sockets.NostrIncomingMessage

data class NostrPublishResult(
    val result: NostrIncomingMessage? = null,
    val error: Throwable? = null,
    val relayUrl: String,
)
