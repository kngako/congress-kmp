package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.nostr.subgroup.SubgroupParentage

/**
 * A group making another group, and the four steps that take.
 *
 * ```
 *  1. ChillDKG among the child's admins  -> threshold key K, and so C = marmotGroupId(K)
 *  2. birth certificate, in the parent   -> the parent's quorum signs C
 *  3. key state, among the child's admins -> the child's quorum signs "C signs with K", carrying it
 *  4. the Marmot room                    -> somebody creates C and welcomes the admins
 * ```
 *
 * None of that order is a policy choice. Step 2 needs `C`, which does not exist
 * until step 1 produces `K`; step 3 carries the certificate, so it needs step 2;
 * and step 4 is gated on the key state for the same reason
 * `DkgRitualViewModel.createAdminGroup` already is -- a room created before its
 * group has agreed what it signs with is a room whose founding fact is settled
 * after the founding.
 *
 * This orchestrates; it does not reimplement. Step 1 is `ChillDkgRitualManager`,
 * steps 2 and 3 are `FrostSigningManager` sessions, and step 4 is
 * `MarmotGroupCreation`. What lives here is the order, the guards, and the
 * reading of what the parent has signed.
 *
 * ### Only step 1 belongs to the coordinator
 *
 * After the ceremony fixes the participant set, every remaining step is open to
 * somebody else: the certificate to any parent admin holding a share of the
 * parent's key, and the key state and the room to any of the child's admins, who
 * by then all hold shares of `K` and know `C`. A coordinator whose phone dies
 * after the ceremony strands nothing. That is why every function here reads its
 * state off stored rows rather than off a session object handed down from the
 * screen that started.
 *
 * See `docs/subgroups.md`.
 */
object SubgroupManager {
    private const val TAG = "SubgroupManager"

    private val logger = Logger.withTag(TAG)

    /**
     * The fewest admins a subgroup can be run by, the coordinator included.
     *
     * The same floor [press.mantra.compose.database.model.types.ChatRoomType.MINIMUM_ROBUST_GROUP_SIZE]
     * states and for the same reason: below three a quorum is not a check, since
     * two admins means every decision needs both of them and one means the
     * coordinator is deciding alone.
     *
     * The coordinator counts. They are a ChillDKG participant by construction and
     * hold a share whether or not anybody ticked their name, so a picker that
     * asked for three *others* would be quietly building a group of four.
     */
    const val MINIMUM_ADMINS: Int = 3

    /**
     * The NIP-17 room a ceremony for these admins would run in.
     *
     * Pure, and the same aggregate `NostrNip17Dao.createNip17ChatRoom` and the
     * inbound path both derive -- which is what makes a ceremony room findable
     * without being announced, and what makes two members starting the same
     * subgroup land in one room rather than two.
     *
     * It is also where the one hard limit comes from. A NIP-17 room's id is a
     * pure function of its members, so **one admin set gets one ceremony room,
     * forever**: picking a set that already holds a completed ceremony would hand
     * back that ceremony's key, and the "new" subgroup would be the old group
     * under a new name. [refuseCeremonyRoom] is where that is caught.
     */
    fun ceremonyRoomIdFor(adminPublicKeys: Set<HexKey>, coordinatorPublicKey: HexKey): String =
        ChatRoom.deriveChatRoomId(adminPublicKeys + coordinatorPublicKey)

    /**
     * Why these admins cannot hold a ceremony together, or null when they can.
     *
     * Read by the picker so a subgroup that could only fail is never offered, and
     * again before anything is published, because a screen not drawing something
     * is not a guard.
     *
     * The message is the user's, so each one says what to do rather than what is
     * wrong.
     */
    suspend fun refuseCeremonyRoom(
        database: MantraDatabase,
        parentRoom: LocalChatRoom,
        adminPublicKeys: Set<HexKey>,
        coordinatorPublicKey: HexKey
    ): String? {
        val admins = adminPublicKeys + coordinatorPublicKey

        if (admins.size < MINIMUM_ADMINS) {
            return "A subgroup needs at least $MINIMUM_ADMINS admins. Pick at least " +
                "${MINIMUM_ADMINS - 1} more people."
        }

        val members = ChillDkgRitualManager.memberPublicKeys(parentRoom)
        if (!members.containsAll(admins)) {
            return "Everyone in a subgroup has to be in this group first."
        }

        // A proper subset, and this is not fussiness. The ceremony room is derived
        // from its members, so selecting the whole group derives the room this
        // group's own ceremony was held in -- and `proposeRitual` would hand back
        // that ceremony, making the "child" this very group.
        if (admins.size == members.size) {
            return "A subgroup cannot be the whole group. Leave at least one member out."
        }

        // Any completed or running ceremony in the room these admins derive is the
        // same trap one step removed: two subgroups with exactly the same admins
        // would share a key and therefore a room id.
        val ceremonyRoomId = ceremonyRoomIdFor(adminPublicKeys, coordinatorPublicKey)
        val running = database.dkgSessionDao().getLatestSessionForChatRoom(ceremonyRoomId)
        if (running != null && running.stage != DkgRitualStage.FAILED) {
            return "These members already hold a shared key together. Change who is in the subgroup."
        }

        return null
    }

    /**
     * Step 2: asks the parent's quorum to certify the child.
     *
     * Runs in the **parent's** room, so the signature is by the parent's key and
     * the author is the parent's own room id -- which is what makes a certificate
     * checkable by anyone holding that id and nothing else.
     *
     * [key] is the *child's* completed ceremony, and it is read for what it
     * produced rather than handed to the session: the parent signs with the
     * parent's key, resolved from the parent's room the way every other signature
     * there is. A caller naming a ceremony here would be a caller choosing what
     * the group signs as.
     *
     * A session of one, never batched, for the reason `GroupKeyStateManager.propose`
     * gives: a batch is only as available as its worst item, and everything after
     * this depends on it.
     */
    suspend fun proposeBirthCertificate(
        database: MantraDatabase,
        parentRoom: LocalChatRoom,
        userPublicKey: HexKey,
        key: DkgSession,
        adminPublicKeys: List<HexKey>,
        name: String,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    ): FrostSigningSession {
        val thresholdPublicKey = key.thresholdPublicKey
            ?: throw IllegalStateException(
                "Ceremony ${key.id} has produced no key for a subgroup to be certified on"
            )

        val subgroupChatRoomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path)
        val parentChatRoomId = parentRoom.chatRoom.id

        require(name.isNotBlank()) { "A subgroup has to be called something" }

        logger.i("Asking $parentChatRoomId to certify $subgroupChatRoomId as its subgroup")

        return FrostSigningManager.proposeSigning(
            database = database,
            localChatRoom = parentRoom,
            userPublicKey = userPublicKey,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = thresholdPublicKey,
                adminPublicKeys = adminPublicKeys,
                name = name,
                path = path
            ),
            content = subgroupChatRoomId
        )
    }

    /**
     * The newest certificate by [parentChatRoomId] that actually certifies
     * [subgroupChatRoomId], or null if this device holds none.
     *
     * **Two certificates for one child is a normal outcome, not a conflict.** Two
     * parent admins can press the button on the same admin set: the second lands
     * in the same ceremony room and gets the first's ceremony back, but both may
     * go on to propose a certificate and both sessions can complete.
     * `GroupSignedEvent` is keyed on the event id, so the rows coexist -- and
     * because both say the same true thing about the same child, which one wins
     * does not matter. The `d` tag makes them replacements of each other rather
     * than an accumulation, which is what it is for.
     */
    suspend fun certificateFor(
        database: MantraDatabase,
        subgroupChatRoomId: String,
        parentChatRoomId: String
    ): GroupSignedEvent? =
        database.groupSignedEventDao()
            .getByChatRoomIdAndKind(parentChatRoomId, SubgroupBirthCertificateEvent.KIND)
            .filter {
                SubgroupBirthCertificateEvent.certifies(
                    event = it.toEvent(),
                    subgroupChatRoomId = subgroupChatRoomId,
                    parentChatRoomId = parentChatRoomId
                )
            }
            .maxByOrNull { it.createdAt }

    /**
     * Step 3: asks the child's quorum to sign its key state, carrying the
     * certificate.
     *
     * Runs where the ceremony ran, because it has to: `GroupKeyStateManager.propose`
     * and `FrostSigningManager.signingPath` both tie a key state to the room its
     * ceremony was held in, and the room the state is *about* does not exist yet.
     *
     * Refuses without a certificate that passes `certifies`. Every device that
     * receives the state runs the same check and drops it when it fails, so
     * proposing one this device would not believe spends a quorum's attention on
     * a statement nobody will keep.
     */
    suspend fun proposeSubgroupKeyState(
        database: MantraDatabase,
        ceremonyRoom: LocalChatRoom,
        userPublicKey: HexKey,
        key: DkgSession,
        parentChatRoomId: HexKey,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    ): FrostSigningSession {
        val thresholdPublicKey = key.thresholdPublicKey
            ?: throw IllegalStateException("Ceremony ${key.id} has produced no key to sign a state for")

        val subgroupChatRoomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path)

        val certificate = certificateFor(database, subgroupChatRoomId, parentChatRoomId)
            ?: throw IllegalStateException(
                "$parentChatRoomId has not certified $subgroupChatRoomId, so its key state " +
                    "cannot name a parent yet"
            )

        return GroupKeyStateManager.propose(
            database = database,
            localChatRoom = ceremonyRoom,
            userPublicKey = userPublicKey,
            key = key,
            path = path,
            parent = SubgroupParentage(
                parentChatRoomId = parentChatRoomId,
                certificate = certificate.toEvent()
            )
        )
    }

    /**
     * A subgroup as the parent's own record has it, plus whatever this device
     * happens to hold about it.
     *
     * [certificate] is the only part that is evidence. [name] and [adminPublicKeys]
     * come off it too and are the **founding** roster -- what the parent approved,
     * not who is in the room now -- so a screen showing them must say so or prefer
     * [room] where there is one.
     */
    data class Subgroup(
        val chatRoomId: String,
        val parentChatRoomId: String,
        val certificate: GroupSignedEvent,
        /** What the parent certified it as. Frozen; a rename does not reach it. */
        val name: String?,
        /** Who the parent certified it for. Frozen; joins and leaves do not reach it. */
        val adminPublicKeys: List<HexKey>,
        /** The room, when this device has one -- which is the live answer to both. */
        val room: LocalChatRoom?,
        /** The child's key state, when this device holds one. */
        val keyState: GroupKeyState?,
    ) {
        /** Whether this device can open the subgroup, rather than only know of it. */
        val isJoined: Boolean get() = room != null
    }

    /**
     * Every subgroup [parentChatRoomId] has certified, as far as this device knows.
     *
     * Read off the parent's signed certificates rather than off a table of
     * subgroups. The certificates *are* the record: they are the group's own
     * signed statement, they are checkable without a lookup, and every device in
     * the parent room already has them -- `FrostSigningManager.complete` files a
     * `GroupSignedEvent` on every device that followed the session, not only on
     * the signers'. A table beside them would be a second copy that can disagree.
     *
     * Includes children whose room this device does not have, which is the normal
     * case for a parent member who is not in the subgroup, and children whose room
     * nobody has created yet, which is the normal case between steps 2 and 4.
     *
     * Newest first, and one entry per child: a second certificate for the same
     * child replaces the first rather than standing beside it.
     */
    suspend fun subgroupsOf(
        database: MantraDatabase,
        parentChatRoomId: String
    ): List<Subgroup> =
        database.groupSignedEventDao()
            .getByChatRoomIdAndKind(parentChatRoomId, SubgroupBirthCertificateEvent.KIND)
            .mapNotNull { signed ->
                val event = signed.toEvent()
                val chatRoomId = SubgroupBirthCertificateEvent.parseSubgroupChatRoomId(event.tags)
                    ?: return@mapNotNull null

                if (!SubgroupBirthCertificateEvent.certifies(event, chatRoomId, parentChatRoomId)) {
                    return@mapNotNull null
                }

                signed to chatRoomId
            }
            .groupBy { (_, chatRoomId) -> chatRoomId }
            .mapNotNull { (chatRoomId, certificates) ->
                val newest = certificates.maxByOrNull { (signed, _) -> signed.createdAt }
                    ?: return@mapNotNull null
                val (signed, _) = newest
                val event = signed.toEvent()

                Subgroup(
                    chatRoomId = chatRoomId,
                    parentChatRoomId = parentChatRoomId,
                    certificate = signed,
                    name = SubgroupBirthCertificateEvent.parseName(event.tags),
                    adminPublicKeys = SubgroupBirthCertificateEvent.parseAdminPublicKeys(event.tags),
                    room = database.chatRoomDao().findChatRoomById(chatRoomId),
                    keyState = database.groupKeyStateDao().getByChatRoomId(chatRoomId),
                )
            }
            .sortedByDescending { it.certificate.createdAt }

    /**
     * The group a room is a subgroup of, verified, or null if it is nobody's
     * child.
     *
     * Off the `GroupKeyState`, which only carries a parent whose certificate was
     * checked -- see `GroupKeyStateManager.stateFrom`. A room whose state this
     * device does not hold answers null even when it really is a subgroup, which
     * is the position a member welcomed in after the founding is in; the
     * unverified hint in the room's own description is the most they get, and it
     * must never be written here.
     */
    suspend fun parentOf(database: MantraDatabase, chatRoomId: String): HexKey? =
        database.groupKeyStateDao().getByChatRoomId(chatRoomId)?.parentChatRoomId

    /**
     * Files a certificate that arrived, or drops it and says why.
     *
     * Reached from two directions and it cannot tell them apart, which is why it
     * checks. `FrostSigningManager.applySignedEvent` calls it with an event whose
     * signature has already been verified against the session's key; the inbound
     * Marmot path calls it with whatever a member put in the room. A member can
     * send a rumor of this kind, so a certificate is believed here on its own
     * merits or not at all.
     *
     * Nothing is written that is not already on file: the `GroupSignedEvent` row
     * is `recordSignedEvents`' to write, and the parent link on the child's room
     * comes off the child's own key state. What this adds is the check and the
     * refusal.
     */
    suspend fun record(
        database: MantraDatabase,
        chatRoomId: String,
        innerEvent: Event
    ): GroupSignedEvent? {
        val subgroupChatRoomId =
            SubgroupBirthCertificateEvent.parseSubgroupChatRoomId(innerEvent.tags)
        if (subgroupChatRoomId == null) {
            logger.w("A birth certificate in $chatRoomId names no subgroup; dropping")
            return null
        }

        if (!SubgroupBirthCertificateEvent.certifies(innerEvent, subgroupChatRoomId, chatRoomId)) {
            logger.w(
                "A birth certificate in $chatRoomId for $subgroupChatRoomId carries no " +
                    "signature by $chatRoomId; dropping"
            )
            return null
        }

        logger.i("$chatRoomId has certified $subgroupChatRoomId as its subgroup")

        return database.groupSignedEventDao().getById(innerEvent.id)
    }
}
