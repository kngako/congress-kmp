package press.mantra.compose.network.sockets

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

fun Flow<NostrIncomingMessage>.filterBySubscriptionId(id: String) =
    filter {
        // Negentropy...
        (it is NostrIncomingMessage.NegentropyMessage && it.subscriptionId == id) ||
        (it is NostrIncomingMessage.NegentropyError && it.subscriptionId == id) ||
        // Events
        (it is NostrIncomingMessage.EventMessage && it.subscriptionId == id) ||
        (it is NostrIncomingMessage.EoseMessage && it.subscriptionId == id) ||
        (it is NostrIncomingMessage.CountMessage && it.subscriptionId == id) ||
        (it is NostrIncomingMessage.EventsMessage && it.subscriptionId == id) ||
        // A relay that terminates our subscription (auth-required, rate-limited,
        // unsupported filter) sends CLOSED instead of EOSE. Without it the collector
        // waits forever on a subscription the relay has already abandoned.
        (it is NostrIncomingMessage.ClosedMessage && it.subscriptionId == id) ||
        // NOTICE carries no subscription id, so it cannot be correlated. It is admitted
        // here only because it is the sole signal some relays give for "negentropy
        // disabled"; the collector treats it as advisory, never as a terminal failure.
        (it is NostrIncomingMessage.NoticeMessage)
    }

// Deliberately does NOT admit NoticeMessage: a NOTICE is not tied to an event id, and
// letting one through failed whatever publish happened to be in flight on the shared
// per-socket flow — including publishes to which the notice had nothing to do.
fun Flow<NostrIncomingMessage>.filterByEventId(id: String) =
    filter {
        it is NostrIncomingMessage.OkMessage && it.eventId == id
    }
