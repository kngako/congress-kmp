package press.mantra.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MarmotInnerEvent

interface MantraRepository {
    suspend fun getArtifacts(chatRoomId: String): List<MantraArtifact>

    suspend fun getArtifact(id: String): MantraArtifact?

    suspend fun getArtifactVersions(artifactId: String): List<MantraArtifactVersion>

    suspend fun getChaptersForArtifact(artifactId: String): List<MantraChapter>

    suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion>

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

            override suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion> = emptyList()

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
