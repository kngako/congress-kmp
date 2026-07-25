package press.mantra.compose.database.repository

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag
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

    override suspend fun getChaptersForArtifact(artifactId: String): List<MantraChapter> =
        database.mantraChapterDao().getChaptersByArtifactId(artifactId)

    override suspend fun getTranslationsForArtifact(artifactId: String): List<MantraTranslationArtifactVersion> =
        database.mantraTranslationArtifactVersionDao().getTranslationsByArtifactId(artifactId)

    override suspend fun getDialects(chatRoomId: String): List<MantraDialect> =
        database.mantraDialectDao().getDialectsByChatRoomId(chatRoomId)

    override suspend fun addDialect(
        name: String,
        country: String,
        language: String,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): MantraDialect? {
        val dialectEventTemplate = DialectEvent.build(
            name = name,
            country = country,
            language = language,
        )

        val mantraDialect = MantraDialect.fromDialectEventTemplate(
            dialectEventTemplate = dialectEventTemplate,
            chatRoomId = chatRoomId,
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
            mantraDialect
        } catch (error: Throwable) {
            logger.e("Failed to add dialect \"$name\" to chat room $chatRoomId", error)
            null
        }
    }

    override suspend fun addArtifact(
        name: String,
        url: String,
        versionLabel: String,
        dialectId: HexKey,
        chatRoomId: String,
        userPublicKey: HexKey,
        visibility: String,
        license: String,
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
            chatRoomId = chatRoomId,
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

            // Every artifact starts with an initial version.
            addArtifactVersion(
                artifactId = mantraArtifact.id,
                versionLabel = versionLabel,
                chatRoomId = chatRoomId,
                userPublicKey = userPublicKey,
            )

            artifactInnerEvent
        } catch (error: Throwable) {
            logger.e("Failed to add artifact \"$name\" to chat room $chatRoomId", error)
            null
        }
    }

    override suspend fun addArtifactVersion(
        artifactId: HexKey,
        versionLabel: String,
        chatRoomId: String,
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
            chatRoomId = chatRoomId,
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

        return versionInnerEvent
    }

    companion object {
        private const val TAG = "DatabaseMantraRepository"
    }
}
