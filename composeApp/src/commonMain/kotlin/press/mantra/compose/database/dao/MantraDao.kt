package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.MantraTranslationChunk
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag
import press.mantra.compose.repository.MantraRepository.Companion.DEFAULT_LICENSE
import press.mantra.compose.repository.MantraRepository.Companion.DEFAULT_VISIBILITY
import press.mantra.compose.text.Markdown

@Dao
abstract class MantraDao(
    private val database: MantraDatabase,
) {
    val logger = Logger.withTag("NostrDao")

    @Transaction
    open suspend fun addDialect(
        localChatRoom: LocalChatRoom,
        name: String,
        country: String,
        language: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
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

        val dialectInnerEvent = MarmotInnerEvent(
            id = mantraDialect.id,
            publicKey = mantraDialect.publicKey,
            kind = DialectEvent.KIND,
            createdAt = mantraDialect.createdAt,
            tags = dialectEventTemplate.tags,
            content = dialectEventTemplate.content,
            chatRoomId = mantraDialect.chatRoomId,
        )

        return try {
            database.mantraDialectDao().upsert(mantraDialect)
            database.marmotInnerEventDao().upsert(dialectInnerEvent)

            sendMarmotInnerEvent(
                localChatRoom = localChatRoom,
                text = "Added $name as a dialect",
                marmotInnerEvent = dialectInnerEvent
            )
            dialectInnerEvent
        } catch (error: Throwable) {
            logger.e("Failed to add dialect \"$name\" to chat room ${localChatRoom.chatRoom.id}", error)
            null
        }

    }

    @Transaction
    open suspend fun addArtifact(
        localChatRoom: LocalChatRoom,
        name: String,
        url: String,
        versionLabel: String,
        dialectId: HexKey,
        userPublicKey: HexKey,
        visibility: String = DEFAULT_VISIBILITY,
        license: String = DEFAULT_LICENSE,
    ): MarmotInnerEvent? {
        // TODO: Verify the active user is an admin of the chat room before allowing this.
        val artifactEventTemplate = ArtifactEvent.build(
            name = name,
            url = url,
            visibility = visibility,
            license = license,
            dialectId = dialectId,
        )

        val mantraArtifact = MantraArtifact.fromArtifactEventTemplate(
            artifactEventTemplate = artifactEventTemplate,
            chatRoomId = localChatRoom.chatRoom.id,
            userPublicKey = userPublicKey,
        ) ?: return null

        // Persist the artifact together with an unprocessed marmot inner event
        // (a rumor). Inner events with a null marmotGroupEventId are later
        // picked up by the outbound pipeline and encrypted into a kind:445
        // group event for the chat room.
        //
        // This covers the "private" visibility case. Permissioned artifacts
        // (published as a PublicMessage) and public artifacts (published as a
        // plain nostr event) are not implemented yet.
        val artifactInnerEvent = MarmotInnerEvent(
            id = mantraArtifact.id,
            publicKey = mantraArtifact.publicKey,
            kind = ArtifactEvent.KIND,
            createdAt = mantraArtifact.createdAt,
            tags = artifactEventTemplate.tags,
            content = artifactEventTemplate.content,
            chatRoomId = mantraArtifact.chatRoomId,
        )

        return try {
            database.mantraArtifactDao().upsert(mantraArtifact)
            database.marmotInnerEventDao().upsert(artifactInnerEvent)

            sendMarmotInnerEvent(
                localChatRoom = localChatRoom,
                text = "Added $name to artifacts",
                marmotInnerEvent = artifactInnerEvent
            )

            // Every artifact starts with an initial version.
            addArtifactVersionInternal(
                localChatRoom = localChatRoom,
                artifactId = mantraArtifact.id,
                versionLabel = versionLabel,
                userPublicKey = userPublicKey,
            )

            artifactInnerEvent
        } catch (error: Throwable) {
            logger.e("Failed to add artifact \"$name\" to chat room ${localChatRoom.chatRoom.id}", error)
            null
        }
    }

    @Transaction
    open suspend fun addArtifactVersion(
        localChatRoom: LocalChatRoom,
        artifactId: HexKey,
        versionLabel: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
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
    ): MarmotInnerEvent? {
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

        val versionInnerEvent = MarmotInnerEvent(
            id = mantraArtifactVersion.id,
            publicKey = mantraArtifactVersion.publicKey,
            kind = ArtifactVersionEvent.KIND,
            createdAt = mantraArtifactVersion.createdAt,
            tags = artifactVersionEventTemplate.tags,
            content = artifactVersionEventTemplate.content,
            chatRoomId = mantraArtifactVersion.chatRoomId,
        )

        database.mantraArtifactVersionDao().upsert(mantraArtifactVersion)
        database.marmotInnerEventDao().upsert(versionInnerEvent)

        sendMarmotInnerEvent(
            localChatRoom = localChatRoom,
            text = "Add the ${artifactVersionEventTemplate.content} version",
            marmotInnerEvent = versionInnerEvent
        )
        return versionInnerEvent
    }

    @Transaction
    open suspend fun addChapter(
        artifactId: String,
        name: String,
        originalText: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {

        // Chapters attach to an artifact version; use the latest one.
        val version = database.mantraArtifactVersionDao()
            .getArtifactVersionsByArtifactId(artifactId)
            .firstOrNull() ?: return null

        val chapterIndex = database.mantraChapterDao()
            .getChaptersByArtifactVersionId(version.id)
            .size

        val chapterEventTemplate = ChapterEvent.build(
            artifactVersionId = version.id,
            name = name,
            originalText = originalText,
            index = chapterIndex,
            wordCount = Markdown.wordCount(originalText),
            characterCount = Markdown.characterCount(originalText),
        )

        val chapter = MantraChapter.fromChapterEventTemplate(
            chapterEventTemplate = chapterEventTemplate,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
        ) ?: return null

        return try {
            database.mantraChapterDao().upsert(chapter)
            val chapterInnerEvent = MarmotInnerEvent(
                id = chapter.id,
                publicKey = chapter.publicKey,
                kind = ChapterEvent.KIND,
                createdAt = chapter.createdAt,
                tags = chapterEventTemplate.tags,
                content = chapterEventTemplate.content,
                chatRoomId = chapter.chatRoomId,
            )
            database.marmotInnerEventDao().upsert(
                chapterInnerEvent
            )

            sendMarmotInnerEvent(
                chatRoomId = chatRoomId,
                userPublicKey = userPublicKey,
                text = "Added chapter to artifact", // TODO: Get artifact to use in text...
                marmotInnerEvent = chapterInnerEvent
            )

            // Split the markdown into paragraph chunks.
            Markdown.splitParagraphs(originalText).forEachIndexed { chunkIndex, paragraph ->
                val chunkEventTemplate = ChunkEvent.build(
                    chapterId = chapter.id,
                    text = paragraph,
                    index = chunkIndex,
                    wordCount = Markdown.wordCount(paragraph),
                    characterCount = Markdown.characterCount(paragraph),
                )
                MantraChunk.fromChunkEventTemplate(
                    chunkEventTemplate = chunkEventTemplate,
                    chatRoomId = chatRoomId,
                    userPublicKey = userPublicKey,
                )?.let { chunk ->
                    database.mantraChunkDao().upsert(chunk)
                    val chunkInnerEvent = MarmotInnerEvent(
                        id = chunk.id,
                        publicKey = chunk.publicKey,
                        kind = ChunkEvent.KIND,
                        createdAt = chunk.createdAt,
                        tags = chunkEventTemplate.tags,
                        content = chunkEventTemplate.content,
                        chatRoomId = chunk.chatRoomId,
                    )

                    database.marmotInnerEventDao().upsert(
                        chunkInnerEvent
                    )

                    sendMarmotInnerEvent(
                        chatRoomId = chatRoomId,
                        userPublicKey = userPublicKey,
                        text = "${chapter.name} added chunk $chunkIndex", // TODO: Use a portion of the actual chunked text...
                        marmotInnerEvent = chunkInnerEvent
                    )
                }
            }

            chapterInnerEvent
        } catch (error: Throwable) {
            logger.e("Failed to add chapter \"$name\" to artifact $artifactId", error)
            null
        }
    }

    @Transaction
    open suspend fun addTranslationArtifactVersion(
        artifactId: String,
        dialectId: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
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
            val translationArtifactVersionInnerEvent = MarmotInnerEvent(
                id = translationVersion.id,
                publicKey = translationVersion.publicKey,
                kind = TranslationArtifactVersionEvent.KIND,
                createdAt = translationVersion.createdAt,
                tags = translationVersionTemplate.tags,
                content = translationVersionTemplate.content,
                chatRoomId = translationVersion.chatRoomId,
            )

            sendMarmotInnerEvent(
                chatRoomId = chatRoomId,
                userPublicKey = userPublicKey,
                text = "Added ${dialect.name} translation",
                marmotInnerEvent = translationArtifactVersionInnerEvent
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
                val translationChapterInnerEvent =  MarmotInnerEvent(
                    id = translationChapter.id,
                    publicKey = translationChapter.publicKey,
                    kind = TranslationChapterEvent.KIND,
                    createdAt = translationChapter.createdAt,
                    tags = translationChapterTemplate.tags,
                    content = translationChapterTemplate.content,
                    chatRoomId = translationChapter.chatRoomId,
                )

                sendMarmotInnerEvent(
                    chatRoomId = chatRoomId,
                    userPublicKey = userPublicKey,
                    text = "Prepared ${dialect.name} translation of the chapter ${chapter.name}",
                    marmotInnerEvent = translationChapterInnerEvent
                )

                // TODO: Don't scaffold translation chunks?
                val chunks = database.mantraChunkDao().getChunksByChapterId(chapter.id)
                chunks.forEach { chunk ->
                    val translationChunkTemplate = TranslationChunkEvent.build(
                        translationChapterId = translationChapter.id,
                        chunkId = chunk.id,
                        index = chunk.index,
                        text = "", // Untranslated scaffold; filled in during authoring.
                    )
                    val translationChunk = MantraTranslationChunk.fromTranslationChunkEventTemplate(
                        translationChunkEventTemplate = translationChunkTemplate,
                        chatRoomId = chatRoomId,
                        userPublicKey = userPublicKey,
                    ) ?: return@forEach

                    database.mantraTranslationChunkDao().upsert(translationChunk)
                    val translationChunkInnerEvent = MarmotInnerEvent(
                        id = translationChunk.id,
                        publicKey = translationChunk.publicKey,
                        kind = TranslationChunkEvent.KIND,
                        createdAt = translationChunk.createdAt,
                        tags = translationChunkTemplate.tags,
                        content = translationChunkTemplate.content,
                        chatRoomId = translationChunk.chatRoomId,
                    )
                    database.marmotInnerEventDao().upsert(
                        translationChunkInnerEvent
                    )

                    sendMarmotInnerEvent(
                        chatRoomId = chatRoomId,
                        userPublicKey = userPublicKey,
                        text = "Prepared translation chunk ${chunk.index}",
                        marmotInnerEvent = translationChunkInnerEvent
                    )
                }
            }

            translationArtifactVersionInnerEvent
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
            // drop the old one (and its rumor) to keep one per source chunk.
            database.mantraTranslationChunkDao()
                .getTranslationChunksByTranslationChapterId(translationChapterId)
                .filter { it.chunkId == chunkId && it.id != translationChunk.id }
                .forEach { stale ->
                    database.mantraTranslationChunkDao().deleteById(stale.id)
                    database.marmotInnerEventDao().deleteById(stale.id)
                }

            database.mantraTranslationChunkDao().upsert(translationChunk)
            val translationChunkInnerEvent = MarmotInnerEvent(
                id = translationChunk.id,
                publicKey = translationChunk.publicKey,
                kind = TranslationChunkEvent.KIND,
                createdAt = translationChunk.createdAt,
                tags = translationChunkTemplate.tags,
                content = translationChunkTemplate.content,
                chatRoomId = translationChunk.chatRoomId,
            )
            sendMarmotInnerEvent(
                chatRoomId = chatRoomId,
                userPublicKey = userPublicKey,
                text = "Translated chunk ${translationChunk.index}", // TODO: Use a portion of the translation and name the language
                marmotInnerEvent = translationChunkInnerEvent
            )

            translationChunk
        } catch (error: Throwable) {
            logger.e("Failed to save translation for chunk $chunkId", error)
            null
        }
    }

    private suspend fun sendMarmotInnerEvent(
        localChatRoom: LocalChatRoom,
        text: String,
        marmotInnerEvent: MarmotInnerEvent
    ) {
        sendMarmotInnerEvent(
            chatRoomId = localChatRoom.chatRoom.id,
            userPublicKey = localChatRoom.chatRoom.userPublicKey,
            text = text,
            marmotInnerEvent = marmotInnerEvent
        )
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