package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.SubgroupOriginEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ProjectEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30330
 *
 * No `toProjectEvent()`, deliberately. The eight `toXEvent()` methods exist to
 * rebuild events for a chronicle in rooms whose work predates `GroupSignedEvent`
 * (schema v13); they are deprecated and there is a removal checklist in
 * docs/member-chronicle.md. Projects arrive at v21, so every project this app
 * will ever hold is on file as a signed event and `signedEventsOf` finds it. A
 * rebuild here would be adding to a mechanism being retired, to serve rows that
 * cannot exist.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatRoomId"),
    ],
)
data class MantraProject(
    @PrimaryKey
    override val id: HexKey,

    /** The room's own key on a group-signed row, which is every project. */
    val publicKey: HexKey,

    override val name: String,
    val description: String,

    /**
     * Who started it, off the event's `creator` tag.
     *
     * Never the submitter, and never filled in from what this device happens to
     * know: the point of the tag is that it is inside the id the quorum signed,
     * and a column that could disagree with it would be worth less than no
     * column. It is the group's claim about whose work this is rather than proof
     * -- the creator signs nothing, the room does -- and the fact about who
     * actually acted is on the `FrostSigningSession`.
     */
    val creatorPublicKey: HexKey,

    /**
     * Who it belongs to: the owner contributor tags, comma-joined and sorted.
     * Read it through [ownerPublicKeySet].
     *
     * One column rather than a join table, following
     * `DkgSession.participantPublicKeys` -- including the sort, which is there so
     * two devices that assembled the set in different orders store the same
     * string. A table would buy querying by owner, which no screen does.
     *
     * Never empty: `ProjectEvent.build` refuses an empty set and
     * [fromProjectEvent] refuses an event with no owners.
     */
    val ownerPublicKeys: String,

    override val chatRoomId: String,

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
): OptionalNostrEventEntity, SubgroupOriginEntity, TimestampedEntity {

    /**
     * Which kind a reader holding only this row's id is looking at.
     *
     * A computed property, so it is not a column and Room never sees it --
     * the kind is a fact about the type rather than about the row, and a
     * stored copy could disagree with the event this row came from.
     */
    override val subgroupOriginKind: Kind get() = ProjectEvent.KIND

    /** The owners as the set they are. Empty only on a row written around this. */
    fun ownerPublicKeySet(): Set<HexKey> = ownerPublicKeys
        .split(",")
        .mapNotNull { key -> key.trim().takeIf { it.isNotEmpty() } }
        .toSet()

    /** Whether [publicKey] owns this, for a screen deciding what to say. */
    fun isOwnedBy(publicKey: HexKey) = publicKey in ownerPublicKeySet()

    companion object {
        fun fromProjectEventTemplate(
            projectEventTemplate: EventTemplate<ProjectEvent>,
            chatRoomId: HexKey,
            userPublicKey: HexKey,
        ): MantraProject? {
            return fromProjectEvent(
                ProjectEvent(
                    id = EventHasher.hashId(
                        pubKey = userPublicKey,
                        tags = projectEventTemplate.tags,
                        content = projectEventTemplate.content,
                        createdAt = projectEventTemplate.createdAt,
                        kind = projectEventTemplate.kind,
                    ),
                    content = projectEventTemplate.content,
                    tags = projectEventTemplate.tags,
                    createdAt = projectEventTemplate.createdAt,
                    pubKey = userPublicKey,
                    sig = "", // Unsigned rumor
                ),
                chatRoomId = chatRoomId,
            )
        }

        /**
         * Null for an event missing anything it was built to carry.
         *
         * The name, the creator and at least one owner are all required, and an
         * event without one of them has either been truncated or is not what it
         * claims. Filling the gap from local knowledge is exactly the failure the
         * tags exist to prevent, so this refuses instead.
         *
         * An empty owner list is also what `ContributorTag.ROLE_OWNER` missing
         * from that tag's known roles looks like from here -- the role parses as
         * null and every owner disappears -- so refusing is the alarm for that
         * bug as well as the guard against a malformed event.
         */
        fun fromProjectEvent(
            projectEvent: ProjectEvent,
            chatRoomId: HexKey,
        ): MantraProject? {
            val name = projectEvent.name() ?: return null
            val creatorPublicKey = projectEvent.creator() ?: return null
            val owners = projectEvent.owners().takeIf { it.isNotEmpty() } ?: return null

            return MantraProject(
                id = projectEvent.id,
                publicKey = projectEvent.pubKey,
                name = name,
                description = projectEvent.content,
                creatorPublicKey = creatorPublicKey,
                // Sorted here as well as in the event. The event's order is what
                // the signature covers; this is so two rows built from the same
                // owners compare equal whatever order they arrived in.
                ownerPublicKeys = owners.toSortedSet().joinToString(","),
                chatRoomId = chatRoomId,
                signature = projectEvent.sig,
                createdAt = Instant.fromEpochSeconds(projectEvent.createdAt),
            )
        }
    }
}
