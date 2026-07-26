package press.mantra.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.MantraTranslationChunk
import press.mantra.compose.database.model.MarmotInnerEvent

interface MantraRepository {
    suspend fun getArtifacts(chatRoomId: String): List<MantraArtifact>

    suspend fun getArtifact(id: String): MantraArtifact?

    suspend fun getArtifactVersions(artifactId: String): List<MantraArtifactVersion>

    suspend fun getChaptersForArtifact(artifactId: String): List<MantraChapter>

    suspend fun getChapter(id: String): MantraChapter?

    suspend fun getChunksForChapter(chapterId: String): List<MantraChunk>

    suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion>

    suspend fun getTranslation(id: String): MantraTranslationArtifactVersion?

    suspend fun getTranslationChapters(translationArtifactVersionId: String): List<MantraTranslationChapter>

    suspend fun getTranslationChunks(translationChapterId: String): List<MantraTranslationChunk>

    /**
     * Start a translation of the artifact into the given dialect: creates a
     * MantraTranslationArtifactVersion for the artifact's latest version, and
     * scaffolds a MantraTranslationChapter for each existing chapter plus a
     * MantraTranslationChunk (empty text) for each existing chunk. Returns null
     * when the artifact has no version to translate.
     */
    suspend fun addTranslation(
        artifactId: String,
        dialectId: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraTranslationArtifactVersion?

    /**
     * Add a chapter (markdown [originalText]) to the artifact's latest version.
     * Word/character counts are derived from the text, and the chapter is split
     * by paragraph into associated chunks. Returns null when the artifact has no
     * version to attach to.
     */
    suspend fun addChapter(
        artifactId: String,
        name: String,
        originalText: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraChapter?

    suspend fun getDialects(chatRoomId: String): List<MantraDialect>

    suspend fun addDialect(
        name: String,
        country: String,
        language: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraDialect?

    suspend fun addArtifact(
        name: String,
        url: String,
        versionLabel: String,
        dialectId: HexKey,
        chatRoomId: String,
        userPublicKey: HexKey,
        visibility: String = DEFAULT_VISIBILITY,
        license: String = DEFAULT_LICENSE,
    ): MarmotInnerEvent?

    suspend fun addArtifactVersion(
        artifactId: HexKey,
        versionLabel: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MarmotInnerEvent?

    companion object {
        const val DEFAULT_VISIBILITY = "private"
        const val DEFAULT_LICENSE = "cc"

        val NO_OP_MANTRA_REPOSITORY = object: MantraRepository{
            override suspend fun getArtifacts(chatRoomId: String): List<MantraArtifact> = emptyList()

            override suspend fun getArtifact(id: String): MantraArtifact? = null

            override suspend fun getArtifactVersions(artifactId: String): List<MantraArtifactVersion> = emptyList()

            override suspend fun getChaptersForArtifact(artifactId: String): List<MantraChapter> = emptyList()

            override suspend fun getChapter(id: String): MantraChapter? = null

            override suspend fun getChunksForChapter(chapterId: String): List<MantraChunk> = emptyList()

            override suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion> = emptyList()

            override suspend fun getTranslation(id: String): MantraTranslationArtifactVersion? = null

            override suspend fun getTranslationChapters(translationArtifactVersionId: String): List<MantraTranslationChapter> = emptyList()

            override suspend fun getTranslationChunks(translationChapterId: String): List<MantraTranslationChunk> = emptyList()

            override suspend fun addTranslation(
                artifactId: String,
                dialectId: String,
                chatRoomId: String,
                userPublicKey: HexKey,
            ): MantraTranslationArtifactVersion? = null

            override suspend fun addChapter(
                artifactId: String,
                name: String,
                originalText: String,
                chatRoomId: String,
                userPublicKey: HexKey,
            ): MantraChapter? = null

            override suspend fun getDialects(chatRoomId: String): List<MantraDialect> = emptyList()

            override suspend fun addDialect(
                name: String,
                country: String,
                language: String,
                chatRoomId: String,
                userPublicKey: HexKey,
            ): MantraDialect? = null

            override suspend fun addArtifact(
                name: String,
                url: String,
                versionLabel: String,
                dialectId: HexKey,
                chatRoomId: String,
                userPublicKey: HexKey,
                visibility: String,
                license: String,
            ): MarmotInnerEvent? = null

            override suspend fun addArtifactVersion(
                artifactId: HexKey,
                versionLabel: String,
                chatRoomId: String,
                userPublicKey: HexKey,
            ): MarmotInnerEvent? = null
        }
    }
}
