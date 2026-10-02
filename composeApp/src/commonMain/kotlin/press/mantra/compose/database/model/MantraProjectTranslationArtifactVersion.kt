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
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30332 -- one translation is part of one project.
 *
 * The other half of [MantraProjectArtifactVersion]; see there for why both
 * references are real foreign keys.
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
            entity = MantraTranslationArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["translationArtifactVersionId"],
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
        Index("projectId"),
        Index("translationArtifactVersionId"),
        Index("chatRoomId"),
    ],
)
data class MantraProjectTranslationArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,

    val projectId: HexKey,
    val translationArtifactVersionId: HexKey,

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
        fun fromProjectTranslationArtifactVersionEventTemplate(
            template: EventTemplate<ProjectTranslationArtifactVersionEvent>,
            chatRoomId: HexKey,
            userPublicKey: HexKey,
        ): MantraProjectTranslationArtifactVersion? {
            return fromProjectTranslationArtifactVersionEvent(
                ProjectTranslationArtifactVersionEvent(
                    id = EventHasher.hashId(
                        pubKey = userPublicKey,
                        tags = template.tags,
                        content = template.content,
                        createdAt = template.createdAt,
                        kind = template.kind,
                    ),
                    content = template.content,
                    tags = template.tags,
                    createdAt = template.createdAt,
                    pubKey = userPublicKey,
                    sig = "", // Unsigned rumor
                ),
                chatRoomId = chatRoomId,
            )
        }

        fun fromProjectTranslationArtifactVersionEvent(
            projectTranslationArtifactVersionEvent: ProjectTranslationArtifactVersionEvent,
            chatRoomId: HexKey,
        ): MantraProjectTranslationArtifactVersion? {
            val projectId =
                projectTranslationArtifactVersionEvent.projectId() ?: return null
            val translationArtifactVersionId =
                projectTranslationArtifactVersionEvent.translationArtifactVersionId()
                    ?: return null

            return MantraProjectTranslationArtifactVersion(
                id = projectTranslationArtifactVersionEvent.id,
                publicKey = projectTranslationArtifactVersionEvent.pubKey,
                projectId = projectId,
                translationArtifactVersionId = translationArtifactVersionId,
                chatRoomId = chatRoomId,
                signature = projectTranslationArtifactVersionEvent.sig,
                createdAt = Instant.fromEpochSeconds(
                    projectTranslationArtifactVersionEvent.createdAt
                ),
            )
        }
    }
}
