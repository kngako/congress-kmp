package ac.cord.auxiliary.compose.ui.composable.widgets.content

import fr.acinq.lightning.payment.Bolt11Invoice

internal sealed interface ContentSegment {
    data class TextSegment(val text: String) : ContentSegment
    data class ImageSegment(val meta: MediaMeta) : ContentSegment
    data class VideoSegment(val meta: MediaMeta) : ContentSegment
    data class AudioSegment(val meta: MediaMeta) : ContentSegment
    data class UnknownMediaSegment(val meta: MediaMeta) : ContentSegment
    data class LinkSegment(val url: String) : ContentSegment
    data class InlineLinkSegment(val url: String) : ContentSegment
    data class NostrNoteSegment(val eventId: String, val relayHints: List<String> = emptyList()) : ContentSegment
    data class NostrProfileSegment(val pubkey: String, val relayHints: List<String> = emptyList()) : ContentSegment
    data class NostrAddressableSegment(val dTag: String, val relays: List<String>, val author: String?, val kind: Int?) : ContentSegment
    data class CustomEmojiSegment(val shortcode: String, val url: String) : ContentSegment
    data class HashtagSegment(val tag: String) : ContentSegment
    data class LightningInvoiceSegment(val invoice: String, val decoded: Bolt11Invoice) : ContentSegment
    data class GroupInviteSegment(val relayUrl: String, val groupId: String) : ContentSegment
}