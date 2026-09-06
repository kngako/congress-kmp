package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.MantraTranslationChunk
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.SubmissionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag
import press.mantra.compose.repository.MantraRepository.Companion.DEFAULT_LICENSE
import press.mantra.compose.repository.MantraRepository.Companion.DEFAULT_VISIBILITY
import kotlin.time.Instant

@Dao
abstract class MantraDao(
    private val database: MantraDatabase,
) {
    val logger = Logger.withTag("NostrDao")

    /**
     * The unsigned nip30303 event [template] describes, authored by [publicKey].
     *
     * Its id is computed the same way the matching Mantra* entity computes
     * its own, so the row on disk and the payload on the wire are the same
     * event rather than two copies of one.
     */
    private fun rumorOf(
        template: EventTemplate<out Event>,
        publicKey: HexKey,
    ): Event = Event(
        id = EventHasher.hashId(
            pubKey = publicKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = publicKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        // A rumor. What signs for this reaching the group is the kind:445 the
        // outbound pipeline wraps it in, not the payload itself.
        sig = "",
    )

    /**
     * Queue [payload] for the group inside a [SubmissionEvent] that
     * [submitterPublicKey] authors.
     *
     * nip30303 events are never sent on their own. Wrapping them means the
     * payload keeps whoever wrote it as its author while the group still knows
     * which member put it there -- see [SubmissionEvent] for why the two are
     * worth telling apart.
     *
     * The submission is stored as an unprocessed rumor (null marmotGroupEventId),
     * which is what the outbound pipeline picks up and encrypts into a kind:445
     * group event for the chat room.
     */
    private suspend fun submitToGroup(
        chatRoomId: String,
        submitterPublicKey: HexKey,
        payload: Event,
        text: String,
    ): MarmotInnerEvent {
        val submissionTemplate = SubmissionEvent.build(payload = payload)

        val submissionInnerEvent = MarmotInnerEvent(
            id = EventHasher.hashId(
                pubKey = submitterPublicKey,
                createdAt = submissionTemplate.createdAt,
                kind = submissionTemplate.kind,
                tags = submissionTemplate.tags,
                content = submissionTemplate.content,
            ),
            publicKey = submitterPublicKey,
            kind = SubmissionEvent.KIND,
            createdAt = Instant.fromEpochSeconds(submissionTemplate.createdAt),
            tags = submissionTemplate.tags,
            content = submissionTemplate.content,
            payloadEventId = payload.id,
            chatRoomId = chatRoomId,
        )

        sendMarmotInnerEvent(
            chatRoomId = chatRoomId,
            userPublicKey = submitterPublicKey,
            text = text,
            marmotInnerEvent = submissionInnerEvent,
        )

        return submissionInnerEvent
    }

    @Transaction
    open suspend fun addDialect(
        localChatRoom: LocalChatRoom,
        name: String,
        country: String,
        language: String,
        userPublicKey: HexKey,
    ): MantraDialect? {
        logger.d("addDialect: $name")

        val dialectEventTemplate = DialectEvent.build(
            name = name,
            country = country,
            language = language,
        )

        val mantraDialect = MantraDialect.fromDialectEventTemplate(
            dialectEventTemplate = dialectEventTemplate,
            chatRoomId = localChatRoom.chatRoom.id,
            userPublicKey = userPublicKey,
        ) ?: return null

        return try {
            database.mantraDialectDao().upsert(mantraDialect)

            submitToGroup(
                chatRoomId = mantraDialect.chatRoomId,
                submitterPublicKey = userPublicKey,
                payload = rumorOf(dialectEventTemplate, userPublicKey),
                text = "Added $name as a dialect",
            )

            mantraDialect
        } catch (error: Throwable) {
            logger.e("Failed to add dialect \"$name\" to chat room ${localChatRoom.chatRoom.id}", error)
            null
        }

    }

    @Transaction
    open suspend fun addArtifactVersion(
        localChatRoom: LocalChatRoom,
        artifactId: HexKey,
        versionLabel: String,
        userPublicKey: HexKey,
    ): MantraArtifactVersion? {
        return addArtifactVersionInternal(
            localChatRoom = localChatRoom,
            artifactId = artifactId,
            versionLabel = versionLabel,
            userPublicKey = userPublicKey
        )
    }

    private suspend fun addArtifactVersionInternal(
        localChatRoom: LocalChatRoom,
        artifactId: HexKey,
        versionLabel: String,
        userPublicKey: HexKey,
    ): MantraArtifactVersion? {
        // The version label is carried in the event content (see
        // MantraArtifactVersion.fromArtifactVersionEvent).
        val artifactVersionEventTemplate = ArtifactVersionEvent.build(
            content = versionLabel,
        ) {
            addUnique(ArtifactIdTag.assemble(artifactId))
        }

        val mantraArtifactVersion = MantraArtifactVersion.fromArtifactVersionEventTemplate(
            artifactVersionEventTemplate = artifactVersionEventTemplate,
            chatRoomId = localChatRoom.chatRoom.id,
            userPublicKey = userPublicKey,
        ) ?: return null

        database.mantraArtifactVersionDao().upsert(mantraArtifactVersion)

        submitToGroup(
            chatRoomId = mantraArtifactVersion.chatRoomId,
            submitterPublicKey = userPublicKey,
            payload = rumorOf(artifactVersionEventTemplate, userPublicKey),
            text = "Add the ${artifactVersionEventTemplate.content} version",
        )

        return mantraArtifactVersion
    }

    @Transaction
    open suspend fun addTranslationArtifactVersion(
        artifactId: String,
        dialectId: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraTranslationArtifactVersion? {
        val artifact = database.mantraArtifactDao().getArtifactById(artifactId) ?: return null
        val version = database.mantraArtifactVersionDao()
            .getArtifactVersionsByArtifactId(artifactId)
            .firstOrNull() ?: return null
        val dialect = database.mantraDialectDao().getDialectById(dialectId) ?: return null

        // The translation is named after its dialect, and inherits the source
        // artifact's visibility/license.
        val translationVersionTemplate = TranslationArtifactVersionEvent.build(
            artifactVersionId = version.id,
            dialectId = dialectId,
            name = dialect.name,
            visibility = artifact.visibility,
            license = artifact.license,
        )
        val translationVersion = MantraTranslationArtifactVersion.fromTranslationArtifactVersionEventTemplate(
            translationArtifactVersionEventTemplate = translationVersionTemplate,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
        ) ?: return null

        return try {
            database.mantraTranslationArtifactVersionDao().upsert(translationVersion)

            submitToGroup(
                chatRoomId = chatRoomId,
                submitterPublicKey = userPublicKey,
                payload = rumorOf(translationVersionTemplate, userPublicKey),
                text = "Added ${dialect.name} translation",
            )

            // Mirror the source structure: a translation chapter per chapter and
            // an (untranslated) translation chunk per chunk.
            val chapters = database.mantraChapterDao().getChaptersByArtifactVersionId(version.id)
            chapters.forEach { chapter ->
                val translationChapterTemplate = TranslationChapterEvent.build(
                    translationArtifactVersionId = translationVersion.id,
                    chapterId = chapter.id,
                    index = chapter.index,
                )
                val translationChapter = MantraTranslationChapter.fromTranslationChapterEventTemplate(
                    translationChapterEventTemplate = translationChapterTemplate,
                    chatRoomId = chatRoomId,
                    userPublicKey = userPublicKey,
                ) ?: return@forEach

                database.mantraTranslationChapterDao().upsert(translationChapter)

                submitToGroup(
                    chatRoomId = chatRoomId,
                    submitterPublicKey = userPublicKey,
                    payload = rumorOf(translationChapterTemplate, userPublicKey),
                    text = "Prepared ${dialect.name} translation of the chapter ${chapter.name}",
                )

                // TODO: Figure out if we seriously need the scaffolding
//                val chunks = database.mantraChunkDao().getChunksByChapterId(chapter.id)
//                chunks.forEach { chunk ->
//                    val translationChunkTemplate = TranslationChunkEvent.build(
//                        translationChapterId = translationChapter.id,
//                        chunkId = chunk.id,
//                        index = chunk.index,
//                        text = "", // Untranslated scaffold; filled in during authoring.
//                    )
//                    val translationChunk = MantraTranslationChunk.fromTranslationChunkEventTemplate(
//                        translationChunkEventTemplate = translationChunkTemplate,
//                        chatRoomId = chatRoomId,
//                        userPublicKey = userPublicKey,
//                    ) ?: return@forEach
//
//                    database.mantraTranslationChunkDao().upsert(translationChunk)
//                    val translationChunkInnerEvent = MarmotInnerEvent(
//                        id = translationChunk.id,
//                        publicKey = translationChunk.publicKey,
//                        kind = TranslationChunkEvent.KIND,
//                        createdAt = translationChunk.createdAt,
//                        tags = translationChunkTemplate.tags,
//                        content = translationChunkTemplate.content,
//                        chatRoomId = translationChunk.chatRoomId,
//                    )
//                    database.marmotInnerEventDao().upsert(
//                        translationChunkInnerEvent
//                    )
//
//                    sendMarmotInnerEvent(
//                        chatRoomId = chatRoomId,
//                        userPublicKey = userPublicKey,
//                        text = "Prepared translation chunk ${chunk.index}",
//                        marmotInnerEvent = translationChunkInnerEvent
//                    )
//                }
            }

            translationVersion
        } catch (error: Throwable) {
            logger.e("Failed to add translation for artifact $artifactId", error)
            null
        }
    }

    @Transaction
    open suspend fun saveTranslation(
        translationChapterId: String,
        chunkId: String,
        text: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraTranslationChunk? {
        // The translation chunk mirrors the source chunk's position.
        val sourceChunk = database.mantraChunkDao().getChunkById(chunkId) ?: return null

        val translationChunkTemplate = TranslationChunkEvent.build(
            translationChapterId = translationChapterId,
            chunkId = chunkId,
            index = sourceChunk.index,
            text = text,
        )
        val translationChunk = MantraTranslationChunk.fromTranslationChunkEventTemplate(
            translationChunkEventTemplate = translationChunkTemplate,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
        ) ?: return null

        return try {
            // Replace any existing translation chunk for this source chunk. Its id
            // is derived from the (now changed) content, so it becomes a new row —
            // drop the old one (and the submission carrying it) to keep one per
            // source chunk. The submission is found by what it carries, since its
            // own id is the envelope's rather than the chunk's.
            database.mantraTranslationChunkDao()
                .getTranslationChunksByTranslationChapterId(translationChapterId)
                .filter { it.chunkId == chunkId && it.id != translationChunk.id }
                .forEach { stale ->
                    database.mantraTranslationChunkDao().deleteById(stale.id)
                    database.marmotInnerEventDao().deleteById(stale.id)
                    database.marmotInnerEventDao().deleteByPayloadEventId(stale.id)
                }

            database.mantraTranslationChunkDao().upsert(translationChunk)

            submitToGroup(
                chatRoomId = chatRoomId,
                submitterPublicKey = userPublicKey,
                payload = rumorOf(translationChunkTemplate, userPublicKey),
                text = "Translated chunk ${translationChunk.index}", // TODO: Use a portion of the translation and name the language
            )

            translationChunk
        } catch (error: Throwable) {
            logger.e("Failed to save translation for chunk $chunkId", error)
            null
        }
    }

    private suspend fun sendMarmotInnerEvent(
        chatRoomId: String,
        userPublicKey: HexKey,
        text: String,
        marmotInnerEvent: MarmotInnerEvent
    ) {
        database.marmotInnerEventDao().upsert(
            marmotInnerEvent
        )

        database.chatMessageDao().upsert(
            ChatMessage(
                content = text,
                chatRoomId = chatRoomId,
                senderPublicKey = userPublicKey,
                isUserMessage = true,
                giftWrapPayloadId = null,
                marmotGroupEventId = null,
                marmotInnerEventId = marmotInnerEvent.id,
            )
        )
        // TODO: Depending on visibility (if signature is non-empty) might want to queue this event for broadcasting...
    }
}