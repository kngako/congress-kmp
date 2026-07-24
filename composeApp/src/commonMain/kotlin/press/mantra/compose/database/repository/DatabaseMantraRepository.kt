package press.mantra.compose.database.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import kotlinx.coroutines.CoroutineScope
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.repository.MantraRepository
import kotlin.time.Instant

class DatabaseMantraRepository(
    val database: MantraDatabase,
    val scope: CoroutineScope
): MantraRepository {
    override suspend fun addArtifact(name: String, url: String, versionLabel: String): MarmotInnerEvent? {
        // Check that user is admin...
        // TODO: Call mantraDao.addArtifact
        val artifactEventTemplate = ArtifactEvent.build(
            name = name,
            url = url,
            visibility = "private",
            license = "cc",
            dialectId = "7de5ab261eac58a01bd46953b5a93b4f80e9501ab01c4adcc9338d8e7507a98d", // TODO: Get this as a paramter...
        )
        // TODO: Save this as an marmotInner event...

        MantraArtifact.fromArtifactEventTemplate(
            artifactEventTemplate = artifactEventTemplate,
            chatRoomId = "",
            userPublicKey = ""
        )?.let { mantraArtifact ->
            database.mantraArtifactDao().upsert(
                mantraArtifact
            )

            // If this is private we create a marmot inner event... if its permissioned we create a marmot publicMessage? if it's public we publish a nostr event?
            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = mantraArtifact.id,
                    publicKey = mantraArtifact.publicKey,
                    createdAt = mantraArtifact.createdAt,
                    tags = artifactEventTemplate.tags,
                    content = artifactEventTemplate.content,
                    chatRoomId = mantraArtifact.chatRoomId
                )
            )

            // also create an artifactVersion
            addArtifactVersion(
                artifactId = mantraArtifact.id,
                versionLabel = versionLabel
            )
        }

        return null
    }

    override suspend fun addArtifactVersion(artifactId: HexKey, versionLabel: String) {
        TODO("Not yet implemented")
    }
}