package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.name
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.SoftDeletableEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.database.model.traits.UserViewableEntity
import press.mantra.compose.exceptions.MarmotUnprocessableInnerEventException
import press.mantra.compose.extensions.toHex
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationEvent
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapPayload::class,
            parentColumns = ["id"],
            childColumns = ["giftWrapPayloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotGroupEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotGroupEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotInnerEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotInnerEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val senderPublicKey: String,
    val isUserMessage: Boolean,

    val giftWrapPayloadId: HexKey?,
    val marmotGroupEventId: HexKey?,
    val marmotInnerEventId: HexKey?,
    val chatRoomId: String,

    val replyToMessageId: Long? = null,
    val quotedMessageId: Long? = null,

    val content: String,
    val messageType: String = "message",

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {

    companion object {
        suspend fun fromGroupEventResult(
            database: MantraDatabase,
            activeKeyPair: KeyPair,
            groupEvent: GroupEvent,
            groupEventResult: GroupEventResult
        ): ChatMessage? {
            return when(groupEventResult) {
                is GroupEventResult.ApplicationMessage -> {
                    val event = Event.fromJsonOrNull(groupEventResult.innerEventJson)
                        ?: throw MarmotUnprocessableInnerEventException("Can't process ${groupEventResult.innerEventJson}")

                    database.marmotInnerEventDao().upsert(
                        MarmotInnerEvent(
                            id = event.id,
                            publicKey = event.pubKey,
                            marmotGroupEventId = groupEvent.id,
                            tags = event.tags,
                            content = event.content,
                            chatRoomId = groupEventResult.groupId,
                            kind = event.kind,
                            createdAt = Instant.fromEpochSeconds(event.createdAt)
                        )
                    )
                    when (event.kind) {
                        ChatEvent.KIND -> {
                            ChatMessage(
                                giftWrapPayloadId = null,
                                messageType = "message",
                                marmotGroupEventId = groupEvent.id,
                                marmotInnerEventId = event.id,
                                senderPublicKey = event.pubKey,
                                isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                chatRoomId = groupEventResult.groupId,
                                createdAt = Instant.fromEpochSeconds(event.createdAt),
                                content = event.content, // TODO: Figure out what to do here...
                            )
                        }
                        ArtifactEvent.KIND -> {
                            // TODO: Handle mantra messages
                            database.mantraArtifactDao().upsert(
                                MantraArtifact(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    url = event.tags.first().first(),
                                    visibility = event.tags.first().first(),
                                    dialectId = event.tags.first().first(),
                                    license = "",
                                    copyrightOwner = "",
                                    signature = event.sig,
                                )
                            )
                            null
                        }

                        ArtifactVersionEvent.KIND -> {
                            database.mantraArtifactVersionDao().upsert(
                                MantraArtifactVersion(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    artifactId = event.tags.first().first(), // TODO: Get artifactId tag...
                                    versionLabel = event.tags.first().first(), // TODO: Get version label from tag...
                                    signature = event.sig
                                )
                            )
                            null
                        }

                        ChapterEvent.KIND -> {
                            database.mantraChapterDao().upsert(
                                MantraChapter(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    artifactVersionId = event.tags.first().first(), // TODO: Get artifactVersionId from tags...
                                    name = event.tags.first().first(), // TODO: Get name from tags...
                                    originalText = event.content, // TODO: Get originalText from tags...
                                    index = 0, // TODO: Get index from tags...
                                    wordCount = 0, // TODO: Get wordCount from tags
                                    characterCount = 0, // TODO: Get characterCount from tags
                                    signature = event.sig
                                )
                            )
                            null
                        }

                        ChunkEvent.KIND -> {
                            database.mantraChunkDao().upsert(
                                MantraChunk(
                                    id =  event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    chapterId = event.tags.first().first(), // TODO: Get chapterId from tags
                                    text = event.content,
                                    index = 0, // TODO: Get index from tags...
                                    wordCount = 0, // TODO: Get wordCount from tags
                                    characterCount = 0, // TODO: Get characterCount from tags
                                    signature = event.sig
                                )
                            )
                            null
                        }
                        DialectEvent.KIND -> {
                            database.mantraDialectDao().upsert(
                                MantraDialect(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    name = event.tags.first().first(), // TODO: Get name from tags
                                    country = event.tags.first().first(), // TODO: Get country from tags
                                    language = event.tags.first().first(), // TODO: Get language from tags
                                    signature = event.sig
                                )
                            )
                            null
                        }

                        TranslationArtifactVersionEvent.KIND -> {
                            database.mantraTranslationArtifactVersionDao().upsert(
                                MantraTranslationArtifactVersion(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    artifactVersionId = event.tags.first().first(), // TODO: Get artifactVersionId from tags...
                                    dialectId = event.tags.first().first(),
                                    name = event.content,
                                    visibility = event.tags.first().first(),
                                    signature = event.sig
                                )
                            )
                            null
                        }
                        TranslationArtifactVersionContributorListEvent.KIND -> {
                            // TODO: Consume contributors...
                            null
                        }
                        TranslationChapterEvent.KIND -> {
                            database.mantraTranslationChapterDao().upsert(
                                MantraTranslationChapter(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    translationArtifactVersionId = event.tags.first().first(), // TODO: Get artifactVersionId from tags...
                                    chapterId = event.tags.first().first(),
                                    index = 0,
                                    signature = event.sig
                                )
                            )
                            null
                        }
                        TranslationChunkEvent.KIND -> {
                            database.mantraTranslationChunkDao().upsert(
                                MantraTranslationChunk(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    chunkId = event.tags.first().first(),
                                    translationChapterId = event.tags.first().first(),
                                    index = 0,
                                    text = event.content,
                                    signature = event.sig
                                )
                            )

                            null
                        }
                        TranslationContributorListEvent.KIND -> {
                            // TODO: Consume contributors...
                            null
                        }

                        TranslationEvent.KIND -> {
                            database.mantraTranslationDao().upsert(
                                MantraTranslation(
                                    id = event.id,
                                    publicKey = event.pubKey,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    translationArtifactVersionId = event.tags.first().first(), // TODO: Get artifactVersionId from tags...
                                    translationChunkId = event.tags.first().first(),
                                    text = event.content,
                                    signature = event.sig
                                )
                            )
                            null
                        }
                        else -> {
                            ChatMessage(
                                giftWrapPayloadId = null,
                                messageType = "unsupported",
                                marmotGroupEventId = groupEvent.id,
                                marmotInnerEventId = event.id,
                                senderPublicKey = groupEvent.pubKey,
                                isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                chatRoomId = groupEventResult.groupId,
                                createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                                content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
                            )
                        }
                    }
                }
                is GroupEventResult.CommitPending -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "pendingCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Pending Commit in epoch ${groupEventResult.epoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.CommitProcessed -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "processedCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Processed Commit in epoch ${groupEventResult.newEpoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.Duplicate -> {

//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "duplicate",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.Error -> {
//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "error",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.ProposalStaged -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "proposalStaged",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Proposal staged: ${groupEventResult.senderLeafIndex}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.UndecryptableOuterLayer -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "undecryptableOuterLayer",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Undecryptable Message", // TODO: Figure out what to do here...
                    )
                }
            }
        }
    }
}