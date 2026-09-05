package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.network.relays.RelaysSocketManager
import press.mantra.compose.network.relays.isRelayBackPressure
import press.mantra.compose.network.sockets.NostrIncomingMessage
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.NostrRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Holds the chat subscriptions open for as long as it runs, so relays push new events at us
 * instead of us asking for them once per screen.
 *
 * See `docs/long-running-sync.md`. The short version: every chat sync in this app used to be
 * a pull — a screen queues a request row, a pump drains it, the relay answers, the
 * subscription is closed — which is structurally incapable of delivering a message that
 * arrives one second after EOSE.
 *
 * This deliberately does NOT share `SynchronizationViewModel`'s subscription semaphore. That
 * budget is four permits across all relays, sized for requests that finish; a subscription
 * that never finishes would hold one forever and permanently halve the backfill throughput.
 * The two budgets have to be read together against what a relay will actually tolerate
 * (commonly ~20 per connection), which they comfortably are.
 */
class LiveSubscriptionManager(
    private val relaysSocketManager: RelaysSocketManager,
    private val nostrRepository: NostrRepository,
) {
    private val logger = Logger.withTag(TAG)

    companion object {
        private const val TAG = "LiveSubscriptionManager"

        /**
         * Stable, and the same on every relay. A subscription id is scoped to its socket, and
         * a relay answers a repeated REQ on an existing id by replacing that subscription's
         * filter — which is what lets a filter be widened later without a close-and-reopen.
         */
        const val GIFT_WRAP_SUB_ID = "live-giftwrap"

        /**
         * How much history to ask for when the subscription is opened.
         *
         * NIP-01 scopes `limit` to the initial query — the stored events a relay sends before
         * EOSE — and explicitly not to the stream that follows. So this bounds what a
         * reconnect costs without touching the live tail at all. Filling in the rest of the
         * history is negentropy's job, not this subscription's.
         */
        private const val INITIAL_HISTORY_LIMIT = 100

        private val INITIAL_REOPEN_DELAY = 5.seconds

        private val MAX_REOPEN_DELAY = 5.minutes
    }

    /**
     * Runs until cancelled. Cancellation is the only exit: the caller scopes this to the
     * active wallet, so a wallet switch tears every subscription down and the new wallet's
     * call builds its own.
     */
    suspend fun observe(keyPair: KeyPair): Unit = coroutineScope {
        val publicKey = keyPair.pubKey.toHexKey()
        logger.i("Opening live subscriptions for $publicKey")

        Relays.DefaultDMRelayList.forEach { relay ->
            launch(Dispatchers.IO) {
                runLiveSubscription(
                    subId = GIFT_WRAP_SUB_ID,
                    relayUrl = relay.url,
                    filters = listOf(giftWrapFilter(publicKey)),
                    keyPair = keyPair,
                )
            }
        }
    }

    /**
     * Every gift wrap addressed to us: direct messages, and the Marmot Welcome events that
     * make us a member of a group.
     *
     * There is deliberately no `since`. NIP-59 randomizes a wrap's `created_at` into the past
     * — our own outbound path stamps them with `TimeUtils.randomWithTwoDays()` — so a wrap
     * published right now can carry a timestamp two days old, and a `since` anywhere near the
     * present would silently drop it. "Some messages just never arrive" is the worst failure
     * mode to debug, and re-receiving a wrap costs one no-op `storeNostrEvent`.
     */
    private fun giftWrapFilter(publicKey: HexKey) = Filter(
        kinds = listOf(GiftWrapEvent.KIND),
        tags = mapOf("p" to listOf(publicKey)),
        limit = INITIAL_HISTORY_LIMIT,
    )

    /**
     * Keeps one subscription open on one relay, re-opening it if the relay ends it.
     *
     * A socket that merely drops needs nothing from here: the pool replays the REQ on
     * reconnect and the collector below, which is attached to the socket client rather than
     * to a session, simply starts receiving again. This loop is for the other case — a relay
     * that answered with CLOSED, which is terminal for that subscription and needs a new one.
     */
    private suspend fun runLiveSubscription(
        subId: String,
        relayUrl: String,
        filters: List<Filter>,
        keyPair: KeyPair,
    ) {
        var reopenDelay = INITIAL_REOPEN_DELAY

        try {
            while (currentCoroutineContext().isActive) {
                val end = try {
                    collectUntilClosed(
                        subId = subId,
                        relayUrl = relayUrl,
                        filters = filters,
                        keyPair = keyPair,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    logger.e("Live subscription $subId on $relayUrl failed", error)
                    SubscriptionEnd(reason = error.message, reachedEose = false)
                }

                // A subscription that got as far as EOSE was working. Whatever ended it is a
                // new problem, not a continuing one, so it does not inherit the old backoff.
                if (end.reachedEose) reopenDelay = INITIAL_REOPEN_DELAY

                reopenDelay = if (isRelayBackPressure(end.reason)) {
                    // Answering "you have too many subscriptions" by opening another one
                    // promptly is how one refusal becomes a flood of them.
                    logger.w("$relayUrl is rate limiting $subId; backing off to the maximum")
                    MAX_REOPEN_DELAY
                } else {
                    minOf(reopenDelay * 2, MAX_REOPEN_DELAY)
                }

                logger.w("Re-opening $subId on $relayUrl in $reopenDelay (ended: ${end.reason})")
                delay(reopenDelay)
            }
        } finally {
            // NonCancellable so a cancelled manager still releases the retained REQ, which is
            // what tells the socket it no longer has a reason to reconnect.
            withContext(NonCancellable) {
                runCatching { relaysSocketManager.closeLiveSubscription(subId, relayUrl) }
                    .onFailure { logger.w(throwable = it) { "Failed to close $subId on $relayUrl" } }
            }
        }
    }

    private data class SubscriptionEnd(
        /** The relay's CLOSED reason, or the failure that ended the collection. */
        val reason: String?,
        /** Whether the relay got as far as sending EOSE before it ended. */
        val reachedEose: Boolean,
    )

    private suspend fun collectUntilClosed(
        subId: String,
        relayUrl: String,
        filters: List<Filter>,
        keyPair: KeyPair,
    ): SubscriptionEnd {
        var reason: String? = null
        var reachedEose = false

        relaysSocketManager.openLiveSubscription(
            reqCommand = ReqCmd(subId = subId, filters = filters),
            relayUrl = relayUrl,
        ).transformWhile { message ->
            emit(message)
            // CLOSED is the only message that ends a live subscription. EOSE emphatically
            // does not: it is the boundary between the stored history and the live tail,
            // and treating it as an end is exactly what makes a sync a poll.
            message !is NostrIncomingMessage.ClosedMessage
        }.collect { message ->
            when (message) {
                is NostrIncomingMessage.EventMessage -> {
                    message.nostrEvent?.let { save(it, relayUrl, keyPair) }
                }

                is NostrIncomingMessage.EventsMessage -> {
                    message.nostrEvents.forEach { save(it, relayUrl, keyPair) }
                }

                is NostrIncomingMessage.EoseMessage -> {
                    reachedEose = true
                    logger.i("$subId on $relayUrl has caught up; now live")
                }

                is NostrIncomingMessage.ClosedMessage -> {
                    reason = message.message
                    logger.w("Relay closed $subId ($relayUrl): ${message.message}")
                }

                is NostrIncomingMessage.NoticeMessage -> {
                    // Advisory only. A NOTICE carries no subscription id, so it is delivered
                    // to every collector on the socket — acting on one here would let an
                    // unrelated relay complaint tear this subscription down.
                    logger.d("Notice while running $subId ($relayUrl): ${message.message}")
                }

                else -> logger.d("Unhandled message on $subId ($relayUrl): $message")
            }
        }

        return SubscriptionEnd(reason = reason, reachedEose = reachedEose)
    }

    /**
     * Saved inline rather than in a launched coroutine, on purpose. Sequential writes keep
     * the events in the order the relay sent them and let the socket's buffer do the
     * back-pressure, instead of fanning a burst out into a coroutine per event.
     */
    private suspend fun save(nostrEvent: NostrEvent, relayUrl: String, keyPair: KeyPair) {
        nostrRepository.saveNostrEvent(
            nostrEvent = nostrEvent,
            relayURL = relayUrl,
            synchronizationRelayURLs = listOf(relayUrl),
            level = 0,
            activeKeyPair = keyPair,
        )
    }
}
