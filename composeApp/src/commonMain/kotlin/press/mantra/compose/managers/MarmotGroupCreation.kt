package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository

/**
 * Creating a Marmot room at an id the group's key derives, once, for every flow
 * that needs one.
 *
 * Two do: the `#admins` room a group opens after its own ceremony, and a
 * subgroup, which is the same act with four different values. This was 120 lines
 * inside `DkgRitualViewModel` and is here unchanged in behaviour, because the
 * rules it already gets right are not rules worth deriving twice.
 *
 * ### The three that are not obvious
 *
 * **Every key package before anything exists.** The id is derived, so there is
 * exactly one room per key at a path; a half-created one occupies that address
 * permanently and there is no second id to retry with. Better to create nothing
 * and name who is missing -- and better still to have checked at the picker, so
 * that a subgroup does not discover it after three ceremonies. See
 * `docs/subgroups.md`.
 *
 * **`adminPubkeys` baked into the epoch-0 group context**, rather than added by a
 * later commit, so a member welcomed afterwards gets a populated group instead of
 * chasing a bootstrap commit that predates their membership. That is why
 * `MarmotGroupData` is built here rather than through `MarmotGroupData.bootstrap`,
 * which hardcodes a single admin.
 *
 * **[GroupKeyStateManager.adopt] before the members are added.** Filing the key
 * state is local and certain; adding members is a relay round trip that can
 * partly fail. Doing it first means the room comes into existence already knowing
 * what it signs with, whatever happens next.
 *
 * ### Derived ids make every step reachable twice
 *
 * A second tap, or another member having got there first, lands on the same id.
 * Joining what exists beats minting a rival group on one address, so [Existing]
 * is a success rather than a refusal.
 */
object MarmotGroupCreation {
    private const val TAG = "MarmotGroupCreation"

    private val logger = Logger.withTag(TAG)

    /** Matches `SelectChatRoomTypeViewModel`: relays are not always prompt. */
    const val KEY_PACKAGE_LOOKUP_TIMEOUT: Long = 10_000L

    sealed interface Outcome {
        /**
         * The room was created. [notAdded] are the members whose invite failed
         * after it existed -- a room with most of the group in it is more useful
         * than no room, and the rest can be invited again.
         */
        data class Created(val room: LocalChatRoom, val notAdded: List<HexKey>) : Outcome

        /** The room was already here. Every path into one ends the same way. */
        data class Existing(val room: LocalChatRoom) : Outcome

        /**
         * Nothing was created, because [missing] have no published key package.
         *
         * The one refusal that is not a failure: creating the room anyway would
         * list an admin who is not in the MLS tree, in a group that disagrees with
         * itself from its first epoch, at an address nothing can replace.
         */
        data class BlockedOn(val missing: List<HexKey>) : Outcome

        data class Failed(val reason: String) : Outcome
    }

    /**
     * Creates the room [groupId] with [adminPublicKeys] as its admins.
     *
     * [purpose] is the human half of the description; the derivation path is
     * appended to it, because MIP-01 has no field for one and the path is what
     * rebuilds the `TweakCache` a signing session needs.
     *
     * [parentChatRoomId] is written onto the room when this is a subgroup. It is
     * the verified value off the child's own key state -- never a claim off a
     * proposal.
     */
    suspend fun create(
        database: MantraDatabase,
        chatRepository: ChatRepository,
        groupId: String,
        name: String,
        purpose: String,
        adminPublicKeys: Set<HexKey>,
        userPublicKey: HexKey,
        keyPair: KeyPair,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
        parentChatRoomId: HexKey? = null,
        keyPackageLookupTimeout: Long = KEY_PACKAGE_LOOKUP_TIMEOUT
    ): Outcome {
        chatRepository.getChatRoomByIdentifier(groupId)?.let { return Outcome.Existing(it) }

        val peers = adminPublicKeys.filterNot { it == userPublicKey }
        val keyPackages = keyPackagesFor(chatRepository, peers, keyPackageLookupTimeout)

        val missing = keyPackages.filter { it.second == null }.map { it.first }
        if (missing.isNotEmpty()) {
            logger.w("Not creating $groupId: no key package for $missing")
            return Outcome.BlockedOn(missing)
        }

        val addable = keyPackages.mapNotNull { (publicKey, keyPackage) ->
            keyPackage?.let { publicKey to it }
        }

        val description = SharedKeyDerivation.describe(purpose = purpose, path = path)

        // Built directly rather than through MarmotGroupData.bootstrap, which
        // hardcodes a single admin.
        val metadata = MarmotGroupData(
            nostrGroupId = groupId,
            name = name,
            description = description,
            adminPubkeys = adminPublicKeys.toList(),
            relays = Relays.DefaultDMRelayList.map { it.url }
        )

        val signingKeyPair = Ed25519.generateKeyPair()
        val group = MlsGroup.create(
            keyPair.pubKey,
            signingKeyPair.privateKey,
            listOf(metadata.toExtension())
        )

        // Keyed on the Marmot nostrGroupId, not MlsGroup's own groupId: they are
        // unrelated 32-byte values, and every inbound path resolves rooms by the
        // former.
        val localChatRoom = chatRepository.getOrCreateChatRoom(
            chatRoomId = groupId,
            activeUserPublicKey = userPublicKey,
            relayHint = null,
            defaultSubject = name,
            description = description,
            mlsGroupState = group.saveState().encodeTls().toHex()
        ) ?: return Outcome.Failed("Couldn't create the group. Please try again.")

        if (parentChatRoomId != null) {
            // Written after the room exists rather than passed into its creation,
            // because `getOrCreateChatRoom` is shared with every other way a room
            // appears and none of them has a parent to hand it.
            database.chatRoomDao().upsert(
                localChatRoom.chatRoom.copy(parentChatRoomId = parentChatRoomId)
            )
        }

        // The room comes into existence already knowing what it signs with. The
        // group agreed that before any of this ran and this device has been
        // holding the signed statement since; this is simply the first moment
        // there is a row to file it against.
        GroupKeyStateManager.adopt(database, groupId)

        val notAdded = runCatching {
            chatRepository.addMembers(localChatRoom = localChatRoom, peers = addable)
        }.onFailure {
            logger.e("Failed to add members to $groupId", it)
        }.getOrElse { addable.map { (publicKey, _) -> publicKey } }

        if (notAdded.isNotEmpty()) {
            logger.w("$groupId created without ${notAdded.size} member(s): $notAdded")
        }

        return Outcome.Created(
            room = chatRepository.getChatRoomByIdentifier(groupId) ?: localChatRoom,
            notAdded = notAdded
        )
    }

    /**
     * Every peer's published key package, or null where there is none.
     *
     * Concurrently, so the whole set costs one relay round trip rather than one
     * each, and with a timeout because a member who has never published one would
     * otherwise hold the flow open forever.
     */
    suspend fun keyPackagesFor(
        chatRepository: ChatRepository,
        publicKeys: List<HexKey>,
        timeout: Long = KEY_PACKAGE_LOOKUP_TIMEOUT
    ): List<Pair<HexKey, MarmotKeyPackage?>> = coroutineScope {
        publicKeys.map { publicKey ->
            async {
                publicKey to withTimeoutOrNull(timeout) {
                    chatRepository.observeMarmotKeyPackageForPublicKey(publicKey)
                        .filterNotNull()
                        .first()
                }
            }
        }.awaitAll()
    }
}
