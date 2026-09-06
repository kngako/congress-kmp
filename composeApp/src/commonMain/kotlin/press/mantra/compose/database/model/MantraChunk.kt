package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip31Alts.AltTag
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ChapterIdTag
import press.mantra.compose.nostr.nip30303.tags.IndexTag
import press.mantra.compose.nostr.nip30303.tags.WordStatisticsTag
import press.mantra.compose.text.Markdown
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30303
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraChapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraChunk(
    @PrimaryKey
    val id: HexKey,
    val chapterId: HexKey,
    val publicKey: HexKey,
    val text: String,
    val index: Int,
    val wordCount: Int,
    val characterCount: Int,

    /**
     * Signature will be an empty string if this is a rumor
     */
    val signature: String,

    val chatRoomId: String,

    val relayHint: String? = null,
    val marmotGroupEventId: HexKey? = null,

    /**
     * Related nostrEventId if this is not a rumor...
     */
    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): OptionalNostrEventEntity, TimestampedEntity {

    fun toChunkEvent(): ChunkEvent {
        return ChunkEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            // Tag order matches ChunkEvent.build so the event id round-trips.
            tags = TagArrayBuilder<ChunkEvent>()
                .addUnique(AltTag.assemble(ChunkEvent.ALT_DESCRIPTION))
                .addUnique(
                    ChapterIdTag.assemble(chapterId)
                )
                .addUnique(
                    IndexTag.assemble(
                        index
                    )
                )
                .addUnique(
                    WordStatisticsTag.assemble(wordCount, characterCount)
                )
                .build(),
            content = text,
            sig = signature
        )
    }

    companion object {
        /**
         * The chunks a chapter is made of, derived from the chapter.
         *
         * The group signs a chapter; it does not sign these. So the chunks
         * cannot be events proposed on their own -- that would cost a quorum
         * per paragraph -- and they cannot be invented by whichever device
         * notices the chapter first, because an invented id differs on every
         * device and none of them would agree about which chunk a translation
         * is of. Deriving them from the signed chapter's own text gives every
         * device the same rows from the same bytes, which is the only property
         * that matters here.
         *
         * They are rumors -- empty signature -- because nobody signed them.
         * What the group signed is the chapter they were split out of, and that
         * is also the only chapter this splits: a chapter that arrived as a
         * submission was sent with its own chunk events, written under the
         * submitter's key, and deriving a second set beside them would leave
         * every paragraph in the chapter twice under ids nothing reconciles.
         *
         * Empty when the chapter has no text to split, which is every chapter
         * whose original text is blank.
         */
        fun chunksOf(
            chapterEvent: ChapterEvent,
            chatRoomId: HexKey,
        ): List<MantraChunk> {
            if (chapterEvent.sig.isEmpty()) return emptyList()

            val originalText = chapterEvent.originalText() ?: return emptyList()

            return Markdown.splitParagraphs(originalText).mapIndexedNotNull { index, paragraph ->
                fromChunkEventTemplate(
                    chunkEventTemplate = ChunkEvent.build(
                        chapterId = chapterEvent.id,
                        text = paragraph,
                        index = index,
                        wordCount = Markdown.wordCount(paragraph),
                        characterCount = Markdown.characterCount(paragraph),
                        // The chapter's own timestamp, not the reader's: a device
                        // applying the chapter an hour later has to arrive at the
                        // same ids as the one that applied it first.
                        createdAt = chapterEvent.createdAt,
                    ),
                    chatRoomId = chatRoomId,
                    userPublicKey = chapterEvent.pubKey,
                )
            }
        }

        fun fromChunkEventTemplate(
            chunkEventTemplate: EventTemplate<ChunkEvent>,
            chatRoomId: HexKey,
            userPublicKey: HexKey,
        ): MantraChunk? {
            return fromChunkEvent(
                ChunkEvent(
                    id = EventHasher.hashId(
                        pubKey = userPublicKey,
                        tags = chunkEventTemplate.tags,
                        content = chunkEventTemplate.content,
                        createdAt = chunkEventTemplate.createdAt,
                        kind = chunkEventTemplate.kind,
                    ),
                    content = chunkEventTemplate.content,
                    tags = chunkEventTemplate.tags,
                    createdAt = chunkEventTemplate.createdAt,
                    pubKey = userPublicKey,
                    sig = "", // Unsigned rumor
                ),
                chatRoomId = chatRoomId,
            )
        }

        fun fromChunkEvent(chunkEvent: ChunkEvent, chatRoomId: HexKey): MantraChunk? {
            return chunkEvent.wordStatisticsTag?.let { wordStatisticsTag ->
                chunkEvent.index()?.let { index ->
                    chunkEvent.chapterId()?.let { chapterId ->
                        MantraChunk(
                            id = chunkEvent.id,
                            publicKey = chunkEvent.pubKey,
                            createdAt = Instant.fromEpochSeconds(chunkEvent.createdAt),
                            index = index,
                            wordCount = wordStatisticsTag.wordCount,
                            characterCount = wordStatisticsTag.characterCount,
                            chapterId = chapterId,
                            text = chunkEvent.content,
                            signature = chunkEvent.sig,
                            chatRoomId = chatRoomId
                        )
                    }

                }
            }
        }
    }
}
