package press.mantra.compose.database.repository

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
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
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown

class DatabaseMantraRepository(
    val database: MantraDatabase,
    val scope: CoroutineScope
): MantraRepository {

    private val logger = Logger.withTag(TAG)

    override suspend fun getArtifacts(chatRoomId: String): List<MantraArtifact> =
        database.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId)

    override suspend fun getArtifact(id: String): MantraArtifact? =
        database.mantraArtifactDao().getArtifactById(id)

    override suspend fun getArtifactVersions(artifactId: String): List<MantraArtifactVersion> =
        database.mantraArtifactVersionDao().getArtifactVersionsByArtifactId(artifactId)

    override suspend fun getArtifactVersion(id: String): MantraArtifactVersion? =
        database.mantraArtifactVersionDao().getArtifactVersionById(id)

    override suspend fun getChaptersForArtifact(artifactId: String): List<MantraChapter> =
        database.mantraChapterDao().getChaptersByArtifactId(artifactId)

    override suspend fun addTranslation(
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
            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = translationVersion.id,
                    publicKey = translationVersion.publicKey,
                    kind = TranslationArtifactVersionEvent.KIND,
                    createdAt = translationVersion.createdAt,
                    tags = translationVersionTemplate.tags,
                    content = translationVersionTemplate.content,
                    chatRoomId = translationVersion.chatRoomId,
                )
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
                database.marmotInnerEventDao().upsert(
                    MarmotInnerEvent(
                        id = translationChapter.id,
                        publicKey = translationChapter.publicKey,
                        kind = TranslationChapterEvent.KIND,
                        createdAt = translationChapter.createdAt,
                        tags = translationChapterTemplate.tags,
                        content = translationChapterTemplate.content,
                        chatRoomId = translationChapter.chatRoomId,
                    )
                )

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
                    database.marmotInnerEventDao().upsert(
                        MarmotInnerEvent(
                            id = translationChunk.id,
                            publicKey = translationChunk.publicKey,
                            kind = TranslationChunkEvent.KIND,
                            createdAt = translationChunk.createdAt,
                            tags = translationChunkTemplate.tags,
                            content = translationChunkTemplate.content,
                            chatRoomId = translationChunk.chatRoomId,
                        )
                    )
                }
            }

            translationVersion
        } catch (error: Throwable) {
            logger.e("Failed to add translation for artifact $artifactId", error)
            null
        }
    }

    override suspend fun getChapter(id: String): MantraChapter? =
        database.mantraChapterDao().getChapterById(id)

    override suspend fun getChunksForChapter(chapterId: String): List<MantraChunk> =
        database.mantraChunkDao().getChunksByChapterId(chapterId)

    override suspend fun getChunk(id: String): MantraChunk? =
        database.mantraChunkDao().getChunkById(id)

    override suspend fun saveTranslationChunk(
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
            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = translationChunk.id,
                    publicKey = translationChunk.publicKey,
                    kind = TranslationChunkEvent.KIND,
                    createdAt = translationChunk.createdAt,
                    tags = translationChunkTemplate.tags,
                    content = translationChunkTemplate.content,
                    chatRoomId = translationChunk.chatRoomId,
                )
            )

            translationChunk
        } catch (error: Throwable) {
            logger.e("Failed to save translation for chunk $chunkId", error)
            null
        }
    }

    override suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion> =
        database.mantraTranslationArtifactVersionDao().getTranslationsByArtifactId(artifactId)

    override suspend fun getTranslation(id: String): MantraTranslationArtifactVersion? =
        database.mantraTranslationArtifactVersionDao().getTranslationById(id)

    override suspend fun getTranslationChapter(id: String): MantraTranslationChapter? =
        database.mantraTranslationChapterDao().getTranslationChapterById(id)

    override suspend fun getTranslationChapters(translationArtifactVersionId: String): List<MantraTranslationChapter> =
        database.mantraTranslationChapterDao().getTranslationChaptersByTranslationArtifactVersionId(translationArtifactVersionId)

    override suspend fun getTranslationChunks(translationChapterId: String): List<MantraTranslationChunk> =
        database.mantraTranslationChunkDao().getTranslationChunksByTranslationChapterId(translationChapterId)

    override suspend fun addChapter(
        artifactId: String,
        name: String,
        originalText: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraChapter? {
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
            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = chapter.id,
                    publicKey = chapter.publicKey,
                    kind = ChapterEvent.KIND,
                    createdAt = chapter.createdAt,
                    tags = chapterEventTemplate.tags,
                    content = chapterEventTemplate.content,
                    chatRoomId = chapter.chatRoomId,
                )
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
                    database.marmotInnerEventDao().upsert(
                        MarmotInnerEvent(
                            id = chunk.id,
                            publicKey = chunk.publicKey,
                            kind = ChunkEvent.KIND,
                            createdAt = chunk.createdAt,
                            tags = chunkEventTemplate.tags,
                            content = chunkEventTemplate.content,
                            chatRoomId = chunk.chatRoomId,
                        )
                    )
                }
            }

            chapter
        } catch (error: Throwable) {
            logger.e("Failed to add chapter \"$name\" to artifact $artifactId", error)
            null
        }
    }

    override suspend fun getDialects(chatRoomId: String): List<MantraDialect> =
        database.mantraDialectDao().getDialectsByChatRoomId(chatRoomId)

    override suspend fun getDialect(id: String): MantraDialect? =
        database.mantraDialectDao().getDialectById(id)

    override suspend fun addDialect(
        localChatRoom: LocalChatRoom,
        name: String,
        country: String,
        language: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
        return database.mantraDao().addDialect(
            localChatRoom = localChatRoom,
            name = name,
            country = country,
            language = language,
            userPublicKey = userPublicKey
        )
    }

    override suspend fun addArtifact(
        localChatRoom: LocalChatRoom,
        name: String,
        url: String,
        versionLabel: String,
        dialectId: HexKey,
        userPublicKey: HexKey,
        visibility: String,
        license: String,
    ): MarmotInnerEvent? {
        return database.mantraDao().addArtifact(
            localChatRoom = localChatRoom,
            name = name,
            url = url,
            versionLabel = versionLabel,
            dialectId = dialectId,
            userPublicKey = userPublicKey,
            visibility = visibility,
            license = license
        )
    }

    override suspend fun addArtifactVersion(
        localChatRoom: LocalChatRoom,
        artifactId: HexKey,
        versionLabel: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
        return database.mantraDao().addArtifactVersion(
            localChatRoom = localChatRoom,
            artifactId = artifactId,
            versionLabel = versionLabel,
            userPublicKey = userPublicKey
        )
    }

    companion object {
        private const val TAG = "DatabaseMantraRepository"
    }
}
