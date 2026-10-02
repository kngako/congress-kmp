package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.time.Clock
import kotlin.time.Instant
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.SoftDeletableEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.database.model.traits.UserViewableEntity

/**
 * One profile's membership of one group: everything about a room that is this
 * profile's rather than the group's.
 *
 * [ChatRoom] used to hold both, keyed by the group id alone, which made it two things
 * in one row. The group's half -- its id, its name, its description -- is the same for
 * every member and is what the twenty-two tables hanging off a room point at. This half
 * is not: an MLS ratchet state belongs to one member, the moment somebody joined is
 * theirs, and a room one profile has archived is still in the other's list.
 *
 * ### Why a row rather than a wider key on the room
 *
 * Keying `ChatRoom` by (`id`, `userPublicKey`) would have been the same isolation and a
 * much worse schema. Of the twenty-two tables that reference a room, fourteen hold the
 * *group's* signed work -- `GroupSignedEvent`, `GroupKeyState` and the twelve `Mantra*`
 * tables -- and two profiles in one group read the same chapters and the same signed
 * events. Those fourteen would have had to either carry a `userPublicKey` that
 * misrepresents them, or drop their foreign key and with it the cascade that cleans a
 * deleted room up. `GroupSignedEvent` could not have carried one at all: its key is the
 * event's hash, so a second copy is not a row the table can hold.
 *
 * Splitting instead leaves all twenty-two pointing at a room that is unambiguously the
 * group's, and puts the profile in the key of the one thing that is a profile's.
 *
 * ### What this does not decide
 *
 * Whether two profiles on a device may both hold one Marmot group. `679d5249` refuses
 * the second one's Welcome, and this removes the reason that refusal existed rather
 * than the refusal itself -- the MLS state it would have overwritten now has somewhere
 * of its own to live. Lifting it is a separate decision about what the app offers.
 */
@Entity(
    primaryKeys = ["chatRoomId", "userPublicKey"],
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["userPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapPayload::class,
            parentColumns = ["id"],
            childColumns = ["initialGiftWrapPayloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("userPublicKey"),
        Index("initialGiftWrapPayloadId"),
    ],
)
data class ChatRoomMembership(
    val chatRoomId: String,

    /** Whose membership this is. */
    val userPublicKey: HexKey,

    /**
     * This member's MLS group state, hex, or null for a NIP-17 room which has none.
     *
     * The clearest reason this table exists. A ratchet state is one member's position in
     * the tree; two members of a group never hold the same one, and writing one over the
     * other breaks both -- in the shape of "the other device never got it", which is the
     * hardest kind of fault to find.
     */
    val mlsGroupState: String? = null,

    /** The gift wrap this profile first opened the room out of, where there was one. */
    val initialGiftWrapPayloadId: HexKey? = null,

    val leftGroupAt: Instant? = null,

    /**
     * When this profile became a member of the group, or null for a membership that
     * predates the column.
     *
     * The pair to [leftGroupAt], and what [memberSince] is really asking for. Written
     * from the moment the group's own clock says this member's epoch began: the
     * Welcome's `created_at`, which the inviter stamps as it mints the Welcome out of
     * the Add commit that made them a member, or the room's own creation for a group
     * this profile started at epoch 0.
     *
     * Stored rather than read off [createdAt] because the two are only incidentally
     * equal. [createdAt] is row bookkeeping -- when this device first wrote the row down
     * -- and the question asked here decides which of the group's messages are this
     * member's to read at all. That is not a fact to leave hanging off a timestamp
     * somebody could reasonably repurpose.
     */
    val joinedGroupAt: Instant? = null,

    /**
     * When this profile last asked the group for its signed history, or null if it never
     * has or the answer has since arrived.
     *
     * A member with no signed work in a room asks for a chronicle -- see
     * `ChronicleManager.requestIfEmpty` and docs/member-chronicle.md. This is the only
     * thing stopping it asking again on every launch while an answer is in flight, and
     * it is cleared as soon as a chronicle applies anything, so a partial answer is
     * followed by another request rather than by silence.
     *
     * Not a claim that anybody replied. Nothing acknowledges a request, and the room
     * having work in it is the only evidence that ever arrives.
     */
    val chronicleRequestedAt: Instant? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = createdAt,

    /** This profile's read state, which the other profile's does not share. */
    override val viewedAt: Instant? = null,

    /**
     * When this profile archived the room, which is a per-profile act.
     *
     * The room goes on existing for the group and for the other profile; what this
     * removes is one person's chat-list row. Which is why the list query filters on
     * this and a lookup by room id does not: a manager asking for the room it is about
     * to act in wants the group, whether or not somebody tidied it away.
     */
    override val deletedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {

    /**
     * The moment this profile joined, falling back to when it wrote the row down.
     *
     * A membership from before [joinedGroupAt] existed has no recorded answer, and
     * [createdAt] is both the best one available and the one every path that writes
     * [joinedGroupAt] would have written anyway: a joiner's row is created from the
     * Welcome, and a creator's when it creates the group.
     */
    val memberSince: Instant get() = joinedGroupAt ?: createdAt

    /**
     * Whether something the group published at [publishedAt] belongs to an epoch this
     * member was never in.
     *
     * MLS gives a joiner the key schedule from their own epoch forward and nothing
     * before it, so a kind:445 older than [memberSince] cannot be read now and cannot be
     * read later -- not by waiting, and not by replaying it. The point of asking is to
     * leave those alone rather than file a line saying a message arrived that nobody can
     * show.
     *
     * Time is the only thing to ask it of. The epoch a kind:445 was encrypted under is
     * inside the outer layer, so an event this device cannot decrypt cannot be asked
     * what epoch it is from, and "before we joined", "from an epoch we have not caught
     * up to" and "from an epoch that fell out of the retention window" all look
     * identical from the outside. What separates the first from the other two is that it
     * was published before the group made the epoch we joined at.
     *
     * Strictly before, so an event stamped in the same second as our Welcome is still
     * read. The error worth avoiding runs one way: an unreadable event costs a wasted
     * decrypt, and a discarded readable one is a message the member never sees. The
     * commit that added us sits exactly on that boundary and is unreadable by
     * construction -- it is the last act of the epoch before ours -- so a room may still
     * show one placeholder for it.
     */
    fun predatesMembership(publishedAt: Instant): Boolean = publishedAt < memberSince

    fun toMlsGroup(): MlsGroup? = mlsGroupState?.let {
        MlsGroup.restore(MlsGroupState.decodeTls(it.hexToByteArray()))
    }
}
