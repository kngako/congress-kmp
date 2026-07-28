package press.mantra.compose.database.repository

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import press.mantra.compose.database.MantraDatabase
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
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.repository.MantraRepository

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

    override suspend fun addTranslationArtifactVersion(
        artifactId: String,
        dialectId: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent? {
        return database.mantraDao().addTranslationArtifactVersion(
            artifactId = artifactId,
            dialectId = dialectId,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey
        )
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
    ): MarmotInnerEvent? {
        return database.mantraDao().addChapter(
            artifactId = artifactId,
            name = name,
            originalText = originalText,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey
        )
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
