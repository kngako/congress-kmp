package ac.cord.auxiliary.compose.ui.composable.widgets.content

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.ui.composable.widgets.content.Constants.INLINE_CONTENT_TAG
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage

@Composable
fun RichContent(
    localNostrEvent: LocalNostrEvent,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = Color.Unspecified,
    emojiMap: Map<String, String> = emptyMap(),
    imetaMap: Map<String, MediaMeta> = emptyMap(),
    plainLinks: Boolean = false,
    onProfileClick: ((String) -> Unit)? = null,
    onNoteClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onLiveStreamClick: ((String, String, String?) -> Unit)? = null,
    noteActions: NoteActions? = null,
    modifier: Modifier = Modifier
) {
    val content = localNostrEvent.nostrEvent.content

    val segments = remember(content, emojiMap, imetaMap, plainLinks) { ContentParser.parse(content.trimEnd('\n', '\r'), emojiMap, imetaMap, trimBlankLines = !plainLinks) }
//    val profileVer = eventRepo?.profileVersion?.collectAsState()?.value ?: 0
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    if (fullScreenImageUrl != null) {
        FullScreenImageViewer(
            imageUrl = fullScreenImageUrl!!,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    // Group segments into inline runs vs block-level items
    val groups = mutableListOf<Any>() // Either MutableList<ContentSegment> (inline run) or ContentSegment (block)
    fun isInline(s: ContentSegment) = s is ContentSegment.TextSegment ||
            s is ContentSegment.HashtagSegment ||
            s is ContentSegment.NostrProfileSegment ||
            s is ContentSegment.CustomEmojiSegment ||
            s is ContentSegment.InlineLinkSegment ||
            (plainLinks && s is ContentSegment.LinkSegment)

    for (segment in segments) {
        if (isInline(segment)) {
            val last = groups.lastOrNull()
            if (last is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                (last as MutableList<ContentSegment>).add(segment)
            } else {
                groups.add(mutableListOf(segment))
            }
        } else {
            groups.add(segment)
        }
    }

    val defaultLinkColor = MaterialTheme.colorScheme.primary
    val effectiveLinkColor = if (linkColor == Color.Unspecified) defaultLinkColor else linkColor
    val linkDecoration = if (linkColor == Color.Unspecified) TextDecoration.None else TextDecoration.Underline
    val uriHandler = LocalUriHandler.current
    val effectiveHashtagClick = onHashtagClick ?: noteActions?.onHashtagClick
    val effectiveRelayClick = noteActions?.onRelayClick

    SelectionContainer {
        Column(modifier = modifier) {
            for (group in groups) {
                if (group is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val inlineSegments = group as List<ContentSegment>

                    // Build profile display names for this run
                    val profilePubkeys = inlineSegments
                        .filterIsInstance<ContentSegment.NostrProfileSegment>()
                        .map { it.pubkey }

                    val profileNames = remember(profilePubkeys) {
                        // TODO: Get these from taggedUsers...
                        val names = mutableMapOf<String, String>()
//                      TODO  for (pubkey in profilePubkeys) {
//                            val profile = localNostrEvent.mentionedProfiles.find { taggedAuthor -> taggedAuthor.publicKey == pubkey }
//                            names[pubkey] = profile?.humanReadableNameOrPubkey()
//                                ?: "${pubkey.take(8)}...${pubkey.takeLast(4)}"
//                        }
                        names
                    }
                    // TODO Queue fetches for any missing profiles
//                    LaunchedEffect(profilePubkeys) {
//                        for (seg in inlineSegments) {
//                            if (seg is ContentSegment.NostrProfileSegment) {
//                                eventRepo?.requestProfileIfMissing(seg.pubkey, seg.relayHints)
//                            }
//                        }
//                    }

                    // Check if run is only whitespace/empty text
                    val hasContent = inlineSegments.any { seg ->
                        when (seg) {
                            is ContentSegment.TextSegment -> seg.text.trim().isNotEmpty()
                            else -> true
                        }
                    }
                    if (!hasContent) continue

                    // Build inline content map for any custom emojis in this run
                    val emojiInlineContent: Map<String, InlineTextContent> =
                        if (inlineSegments.any { it is ContentSegment.CustomEmojiSegment }) {
                            val emojiSize = style.fontSize
                            inlineSegments
                                .filterIsInstance<ContentSegment.CustomEmojiSegment>()
                                .distinctBy { it.shortcode }
                                .associate { seg ->
                                    seg.shortcode to InlineTextContent(
                                        placeholder = Placeholder(
                                            width = emojiSize * 1.5f,
                                            height = emojiSize * 1.3f,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        AsyncImage(
                                            model = seg.url,
                                            contentDescription = seg.shortcode,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                        } else emptyMap()

                    val annotated = buildAnnotatedString {
                        for (seg in inlineSegments) {
                            when (seg) {
                                is ContentSegment.TextSegment -> {
                                    withStyle(SpanStyle(color = color)) {
                                        append(seg.text)
                                    }
                                }
                                is ContentSegment.CustomEmojiSegment -> {
                                    pushStringAnnotation(
                                        tag = INLINE_CONTENT_TAG,
                                        annotation = seg.shortcode
                                    )
                                    append(":${seg.shortcode}:")
                                    pop()
                                }
                                is ContentSegment.HashtagSegment -> {
                                    val tag = seg.tag
                                    withLink(
                                        LinkAnnotation.Clickable("hashtag") {
                                            effectiveHashtagClick?.invoke(tag)
                                        }
                                    ) {
                                        withStyle(SpanStyle(color = effectiveLinkColor, textDecoration = linkDecoration)) {
                                            append("#${seg.tag}")
                                        }
                                    }
                                }
                                is ContentSegment.NostrProfileSegment -> {
                                    val pubkey = seg.pubkey
                                    val displayName = profileNames[seg.pubkey] ?: seg.pubkey.take(8)
                                    withLink(
                                        LinkAnnotation.Clickable("profile") {
                                            onProfileClick?.invoke(pubkey)
                                        }
                                    ) {
                                        withStyle(SpanStyle(color = effectiveLinkColor, textDecoration = linkDecoration)) {
                                            append("@${displayName.trimEnd()}")
                                        }
                                    }
                                }
                                is ContentSegment.LinkSegment -> {
                                    val linkUrl = seg.url
                                    val displayUrl = linkUrl.removePrefix("https://").removePrefix("http://")
                                    withLink(
                                        LinkAnnotation.Clickable("url") {
                                            uriHandler.openUri(linkUrl)
                                        }
                                    ) {
                                        withStyle(SpanStyle(color = effectiveLinkColor, textDecoration = linkDecoration)) {
                                            append(displayUrl)
                                        }
                                    }
                                }
                                is ContentSegment.InlineLinkSegment -> {
                                    val linkUrl = seg.url
                                    val isRelay = linkUrl.startsWith("wss://") || linkUrl.startsWith("ws://")
                                    val displayUrl = linkUrl
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                    withLink(
                                        LinkAnnotation.Clickable("url") {
                                            if (isRelay) {
                                                effectiveRelayClick?.invoke(linkUrl)
                                            } else {
                                                uriHandler.openUri(linkUrl)
                                            }
                                        }
                                    ) {
                                        withStyle(SpanStyle(color = effectiveLinkColor, textDecoration = linkDecoration)) {
                                            append(displayUrl)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }

                    Text(text = annotated, style = style, inlineContent = emojiInlineContent)
                } else {
                    val segment = group as ContentSegment
                    when (segment) {
//                        is ContentSegment.ImageSegment -> {
//                            ImageWithContextMenu(
//                                meta = segment.meta,
//                                onFullScreen = { fullScreenImageUrl = segment.meta.url }
//                            )
//                        }
//                        is ContentSegment.VideoSegment -> {
//                            InlineVideoPlayerWithFullscreen(
//                                meta = segment.meta,
//                                onFullScreen = { positionMs ->
//                                    FullScreenVideoState.enter(segment.meta.url, positionMs)
//                                }
//                            )
//                        }
//                        is ContentSegment.AudioSegment -> {
//                            InlineAudioPlayer(meta = segment.meta)
//                        }
//                        is ContentSegment.UnknownMediaSegment -> {
//                            UnknownMediaContent(
//                                meta = segment.meta,
//                                onFullScreenImage = { fullScreenImageUrl = segment.meta.url },
//                                onFullScreenVideo = { positionMs ->
//                                    FullScreenVideoState.enter(segment.meta.url, positionMs)
//                                }
//                            )
//                        }
                        is ContentSegment.LinkSegment -> {
                            LinkPreview(url = segment.url)
                        }
                        is ContentSegment.NostrNoteSegment -> {
                            if (localNostrEvent.quotedRelation != null) {
                                QuotedNote(
                                    quotedNostrEvent = localNostrEvent.quotedRelation,
                                    relayHints = segment.relayHints,
                                    onNoteClick = onNoteClick,
                                    noteActions = noteActions
                                )
                            } else {
                                Text(
                                    text = "nostr:${segment.eventId.take(8)}...",
                                    style = style,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is ContentSegment.NostrAddressableSegment -> {
                            val kind = segment.kind
                            when {
//                                kind == 1 || kind == 0 -> {
//                                    if (localNostrEvent.quotedPost != null && segment.author != null) {
//                                        QuotedAddressableNote(
//                                            kind = kind,
//                                            dTag = segment.dTag,
//                                            author = segment.author,
//                                            relayHints = segment.relays,
//                                            onNoteClick = onNoteClick,
//                                            onProfileClick = onProfileClick,
//                                            noteActions = noteActions,
//                                            style = style
//                                        )
//                                    } else {
//                                        Text(
//                                            text = "nostr:${segment.dTag.take(12)}...",
//                                            style = style,
//                                            color = MaterialTheme.colorScheme.primary
//                                        )
//                                    }
//                                }
//                                kind == 30023 -> {
//                                    if (segment.author != null) {
//                                        // TODO: Add support for articles in localNostrEvents...
//                                        ArticleCard(
//                                            dTag = segment.dTag,
//                                            author = segment.author,
//                                            relayHints = segment.relays,
//                                            eventRepo = eventRepo,
//                                            onArticleClick = noteActions?.onArticleClick,
//                                            onProfileClick = onProfileClick
//                                        )
//                                    } else {
//                                        UnsupportedKindBadge(kind = kind, style = style)
//                                    }
//                                }
//                                kind == 30311 -> {
//                                    if (eventRepo != null && segment.author != null) {
//                                        LiveStreamCard(
//                                            dTag = segment.dTag,
//                                            author = segment.author,
//                                            relayHints = segment.relays,
//                                            eventRepo = eventRepo,
//                                            onProfileClick = onProfileClick,
//                                            onLiveStreamClick = onLiveStreamClick ?: noteActions?.onLiveStreamClick,
//                                            segmentRelayHints = segment.relays
//                                        )
//                                    } else {
//                                        UnsupportedKindBadge(kind = kind, style = style)
//                                    }
//                                }
//                                kind == 30030 -> {
//                                    if (eventRepo != null && segment.author != null) {
//                                        EmojiPackCard(
//                                            dTag = segment.dTag,
//                                            author = segment.author,
//                                            relayHints = segment.relays,
//                                            eventRepo = eventRepo,
//                                            onProfileClick = onProfileClick,
//                                            onAddEmojiSet = noteActions?.onAddEmojiSet,
//                                            onRemoveEmojiSet = noteActions?.onRemoveEmojiSet,
//                                            isEmojiSetAdded = noteActions?.isEmojiSetAdded
//                                        )
//                                    } else {
//                                        UnsupportedKindBadge(kind = kind, style = style)
//                                    }
//                                }
                                else -> UnsupportedKindBadge(kind = kind, style = style)
                            }
                        }
                        is ContentSegment.LightningInvoiceSegment -> {
                            LightningInvoiceCard(
                                invoice = segment.invoice,
                                decoded = segment.decoded,
                                onPayInvoice = noteActions?.onPayInvoice
                            )
                        }
//                        is ContentSegment.GroupInviteSegment -> {
//                            GroupInviteCard(
//                                relayUrl = segment.relayUrl,
//                                groupId = segment.groupId,
//                                initialMetadata = noteActions?.groupMetadataProvider?.invoke(segment.relayUrl, segment.groupId),
//                                onGroupRoom = noteActions?.onGroupRoom,
//                                onFetchPreview = noteActions?.fetchGroupPreview,
//                                eventRepo = eventRepo
//                            )
//                        }
                        else -> {
                            Text(
                                text = "Unsupported Content"
                            )
                        }
                    }
                }
            }
        }
    }
}