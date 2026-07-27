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
                            MantraArtifact.fromArtifactEvent(
                                artifactEvent = ArtifactEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig,
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraArtifact ->
                                database.mantraArtifactDao().upsert(
                                    mantraArtifact.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )

                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "artifact",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraArtifact.name} to artifacts"
                                )
                            }
                        }
                        ArtifactVersionEvent.KIND -> {
                            MantraArtifactVersion.fromArtifactVersionEvent(
                                artifactVersionEvent = ArtifactVersionEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraArtifactVersion ->
                                database.mantraArtifactVersionDao().upsert(
                                    mantraArtifactVersion.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )

                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "artifactVersion",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraArtifactVersion.versionLabel} to artifact versions" // TODO: Use artifact name...
                                )
                            }
                        }
                        ChapterEvent.KIND -> {
                            MantraChapter.fromChapterEvent(
                                chapterEvent = ChapterEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraChapter ->
                                database.mantraChapterDao().upsert(
                                    mantraChapter.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )

                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "chapter",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraChapter.name} to chapters" // TODO: Use artifact name...
                                )
                            }
                        }

                        ChunkEvent.KIND -> {
                            MantraChunk.fromChunkEvent(
                                chunkEvent = ChunkEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraChunk ->
                                database.mantraChunkDao().upsert(
                                    mantraChunk.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )

                                // TODO: Chunks might be too noisy to show in chat...
                            }
                            null
                        }
                        DialectEvent.KIND -> {
                            MantraDialect.fromDialectEvent(
                                dialectEvent = DialectEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraDialect ->
                                database.mantraDialectDao().upsert(
                                    mantraDialect.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )


                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "dialect",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraDialect.name} to dialects" // TODO: Use artifact name...
                                )
                            }
                        }
                        TranslationArtifactVersionEvent.KIND -> {
                            MantraTranslationArtifactVersion.fromTranslationArtifactVersionEvent(
                                translationArtifactVersionEvent = TranslationArtifactVersionEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraTranslationArtifactVersion ->
                                database.mantraTranslationArtifactVersionDao().upsert(
                                    mantraTranslationArtifactVersion.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )

                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "translationArtifactVersion",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraTranslationArtifactVersion.name} to translation artifact versions" // TODO: Use artifact name...
                                )
                            }
                        }
                        TranslationArtifactVersionContributorListEvent.KIND -> {
                            // TODO: Consume contributors...
                            null
                        }
                        TranslationChapterEvent.KIND -> {
                            MantraTranslationChapter.fromTranslationChapterEvent(
                                translationChapterEvent = TranslationChapterEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraTranslationChapter ->
                                database.mantraTranslationChapterDao().upsert(
                                    mantraTranslationChapter.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )
                                // TODO: translation chapter might be too noisy for chat updates
                            }
                            null
                        }
                        TranslationChunkEvent.KIND -> {
                            MantraTranslationChunk.fromTranslationChunkEvent(
                                translationChunkEvent = TranslationChunkEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraTranslationChunk ->
                                database.mantraTranslationChunkDao().upsert(
                                    mantraTranslationChunk.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )


                            }
                            null
                        }
                        TranslationContributorListEvent.KIND -> {
                            // TODO: Consume contributors...
                            null
                        }
                        TranslationEvent.KIND -> {
                            MantraTranslation.fromTranslationEvent(
                                translationEvent = TranslationEvent(
                                    id = event.id,
                                    pubKey = event.pubKey,
                                    createdAt = event.createdAt,
                                    tags = event.tags,
                                    content = event.content,
                                    sig = event.sig
                                ),
                                chatRoomId = groupEventResult.groupId,
                            )?.let { mantraTranslation ->
                                database.mantraTranslationDao().upsert(
                                    mantraTranslation.copy(
                                        marmotGroupEventId = groupEvent.id,
                                    )
                                )
                                ChatMessage(
                                    giftWrapPayloadId = null,
                                    messageType = "translation",
                                    marmotGroupEventId = groupEvent.id,
                                    marmotInnerEventId = event.id,
                                    senderPublicKey = event.pubKey,
                                    isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                    chatRoomId = groupEventResult.groupId,
                                    createdAt = Instant.fromEpochSeconds(event.createdAt),
                                    content = "Added ${mantraTranslation.text} to translations" // TODO: Reference original text
                                )
                            }
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