package press.mantra.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MarmotInnerEvent

interface MantraRepository {
    suspend fun addArtifact(
        name: String,
        url: String,
        versionLabel: String
    ): MarmotInnerEvent?

    suspend fun addArtifactVersion(
        artifactId: HexKey,
        versionLabel: String
    )

    companion object {
        val NO_OP_MANTRA_REPOSITORY = object: MantraRepository{
            override suspend fun addArtifact(
                name: String,
                url: String,
                versionLabel: String
            ): MarmotInnerEvent? {
                TODO("Not yet implemented")
            }

            override suspend fun addArtifactVersion(
                artifactId: HexKey,
                versionLabel: String
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}