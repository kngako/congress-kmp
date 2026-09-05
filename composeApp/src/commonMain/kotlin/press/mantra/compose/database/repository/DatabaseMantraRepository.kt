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
    ): MantraTranslationArtifactVersion? {
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
        return database.mantraDao().saveTranslation(
            translationChapterId = translationChapterId,
            chunkId = chunkId,
            text = text,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey
        )
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
    ): MantraDialect? {
        return database.mantraDao().addDialect(
            localChatRoom = localChatRoom,
            name = name,
            country = country,
            language = language,
            userPublicKey = userPublicKey
        )
    }

    override suspend fun addArtifactVersion(
        localChatRoom: LocalChatRoom,
        artifactId: HexKey,
        versionLabel: String,
        userPublicKey: HexKey,
    ): MantraArtifactVersion? {
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
