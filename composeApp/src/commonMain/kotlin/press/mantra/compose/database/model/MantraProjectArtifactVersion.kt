package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30331 -- one source version is part of one project.
 *
 * Both references are real foreign keys, which is the whole of the
 * one-room scope decision: a proposal naming a version this room does not hold
 * fails loudly at apply time rather than landing as a row pointing at nothing.
 * Cross-room projects are Phase 9 of docs/project-feature.md and start by taking
 * these away, following `parentChatRoomId`'s precedent -- a plain column, never a
 * foreign key, because a dangling value is the normal state there.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MantraArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["artifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Every read of this table is "this project's contents".
        Index("projectId"),
        // And the inverse, for an artifact version saying which projects it is in.
        Index("artifactVersionId"),
        Index("chatRoomId"),
    ],
)
data class MantraProjectArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,

    val projectId: HexKey,
    val artifactVersionId: HexKey,

    val chatRoomId: String,

    /** Signature will be an empty string if this is a rumor */
    val signature: String,

    val relayHint: String? = null,
    val marmotGroupEventId: HexKey? = null,

    /** The [GroupSignedEvent] this row was made from, when the group signed it. */
    val groupSignedEventId: HexKey? = null,

    /** Related nostrEventId if this is not a rumor... */
    override val nostrEventId: HexKey? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): OptionalNostrEventEntity, TimestampedEntity {

    companion object {
        fun fromProjectArtifactVersionEventTemplate(
            projectArtifactVersionEventTemplate: EventTemplate<ProjectArtifactVersionEvent>,
            chatRoomId: HexKey,
            userPublicKey: HexKey,
        ): MantraProjectArtifactVersion? {
            return fromProjectArtifactVersionEvent(
                ProjectArtifactVersionEvent(
                    id = EventHasher.hashId(
                        pubKey = userPublicKey,
                        tags = projectArtifactVersionEventTemplate.tags,
                        content = projectArtifactVersionEventTemplate.content,
                        createdAt = projectArtifactVersionEventTemplate.createdAt,
                        kind = projectArtifactVersionEventTemplate.kind,
                    ),
                    content = projectArtifactVersionEventTemplate.content,
                    tags = projectArtifactVersionEventTemplate.tags,
                    createdAt = projectArtifactVersionEventTemplate.createdAt,
                    pubKey = userPublicKey,
                    sig = "", // Unsigned rumor
                ),
                chatRoomId = chatRoomId,
            )
        }

        fun fromProjectArtifactVersionEvent(
            projectArtifactVersionEvent: ProjectArtifactVersionEvent,
            chatRoomId: HexKey,
        ): MantraProjectArtifactVersion? {
            val projectId = projectArtifactVersionEvent.projectId() ?: return null
            val artifactVersionId =
                projectArtifactVersionEvent.artifactVersionId() ?: return null

            return MantraProjectArtifactVersion(
                id = projectArtifactVersionEvent.id,
                publicKey = projectArtifactVersionEvent.pubKey,
                projectId = projectId,
                artifactVersionId = artifactVersionId,
                chatRoomId = chatRoomId,
                signature = projectArtifactVersionEvent.sig,
                createdAt = Instant.fromEpochSeconds(projectArtifactVersionEvent.createdAt),
            )
        }
    }
}
