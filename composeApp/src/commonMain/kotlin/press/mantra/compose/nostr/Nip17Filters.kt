package press.mantra.compose.nostr

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import press.mantra.compose.database.model.types.SynchronizationFilter

/**
 * The one filter shape that can return a NIP-17 message we are able to read.
 *
 * A gift wrap hides everything except who it is for. The author is the throwaway
 * key [GiftWrapEvent.create] mints and discards, the content is sealed to the
 * recipient, and `created_at` is randomised up to two days into the past. That
 * leaves the `p` tag as the only clause worth writing, and it has to name us:
 * naming a peer subscribes to mail no key of ours can open, and adding `authors`
 * matches nothing on any relay, ever. Both mistakes were live in three separate
 * call sites, so the filter is built in one place now and asserted in one place.
 */
object Nip17Filters {

    /**
     * Everything gift-wrapped to [publicKey], capped at [limit] events.
     *
     * Deliberately carries no `since`. NIP-59 back-dates a wrap by up to two days,
     * so a cursor built from the newest wrap we hold silently skips mail that was
     * sent later but stamped earlier.
     */
    fun inbox(
        publicKey: HexKey,
        limit: Int = DEFAULT_LIMIT,
    ) = SynchronizationFilter(
        kinds = arrayOf(GiftWrapEvent.KIND),
        tags = mapOf("p" to listOf(publicKey)),
        limit = limit,
    )

    const val DEFAULT_LIMIT = 50
}
