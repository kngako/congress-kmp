package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.nostr.subgroup.SubgroupLineage
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
 * **All four happen in the parent's room**, bar the last, which makes a room of
 * its own. Steps 1 and 3 are held there because the child has nowhere else to be:
 * its id is derived from the key step 1 produces, so it cannot exist until
 * afterwards. Step 2 is there because the signature is the parent's.
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
     * How far an ancestor walk goes before it gives up.
     *
     * A lineage is a DAG rather than a tree once a room can have two parents, so
     * "is this group already beneath that one" is a graph walk. Bounded because a
     * cycle is constructible: a child's quorum can certify its own parent -- the
     * parent's threshold key is public -- and the parent's quorum can re-announce
     * a state accepting it. Both have to cooperate in something pointless, which
     * is no reason to let a reader loop forever.
     */
    const val MAXIMUM_LINEAGE_DEPTH: Int = 16

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
    suspend fun refuseSubgroup(
        database: MantraDatabase,
        parentRoom: LocalChatRoom,
        adminPublicKeys: Set<HexKey>,
        coordinatorPublicKey: HexKey
    ): String? {
        val admins = adminPublicKeys + coordinatorPublicKey
        val parentChatRoomId = parentRoom.chatRoom.id

        // A subgroup is certified by the parent's key, so a device holding no
        // share of it cannot open the session at all --
        // `FrostSigningManager.proposeSigningBatch` throws outright, and throwing
        // at a button is not a UI. Checked before the size rules because it is the
        // one refusal the user cannot fix by picking differently.
        if (!FrostSigningManager.canSign(database, parentChatRoomId)) {
            return "Only a member who holds a share of this group's key can make a subgroup."
        }

        // A non-admin proposing the group's signature is a proposal the admins
        // have to decline by hand, which is a worse outcome than not offering it.
        //
        // Read off the room's own `MarmotGroupData`, not off `Participant.adminAt`.
        // That column is written in exactly one place -- `processGroupMembershipChanges`,
        // on an arriving commit -- so on the device that *created* a room it is
        // null for everybody, including the creator, until some other member
        // sends a commit. A freshly made room has had no commits, so the flag
        // says nobody administers a group whose epoch-0 context names three
        // admins.
        //
        // The group data is the thing that column is a cache of: MIP-01 stamps
        // `admin_pubkeys` into the epoch-0 group context, every member gets it in
        // their Welcome, and `processGroupMembershipChanges` reads exactly this
        // when it writes the flag. Reading it directly cannot drift from what the
        // group agreed, and is right on both sides from the first moment.
        // Named for the parent, because `adminPublicKeys` in this function is the
        // *subgroup's* picked admins and the two must never be confused -- the
        // ceremony is opened over the second while running in a room governed by
        // the first.
        val parentAdminPublicKeys = runCatching {
            parentRoom.chatRoom.toMlsGroup()?.currentMarmotData()?.adminPubkeys
        }.getOrNull()

        if (parentAdminPublicKeys == null) {
            // No MLS state, or state this build cannot read. A NIP-17 room has no
            // admin list to consult and no group context to put a subgroup's
            // certificate in, which is the same reason the button is hidden there.
            return "A subgroup can only be made from a group that has admins."
        }

        if (!parentAdminPublicKeys.contains(coordinatorPublicKey)) {
            return "Only an admin of this group can make a subgroup."
        }

        if (admins.size < MINIMUM_ADMINS) {
            return "A subgroup needs at least $MINIMUM_ADMINS admins. Pick at least " +
                "${MINIMUM_ADMINS - 1} more people."
        }

        val members = ChillDkgRitualManager.memberPublicKeys(parentRoom)
        if (!members.containsAll(admins)) {
            return "Everyone in a subgroup has to be in this group first."
        }

        // A subgroup *may* be the whole group. It is a logical division -- a group
        // deciding that some of its work belongs to a differently-keyed room --
        // rather than a smaller membership, so "everybody" is a normal answer and
        // never this function's business to refuse.
        //
        // The only thing left to refuse is a ceremony already under way for the
        // same subgroup, and it is refused because a person cannot answer for two
        // of them: every admin would be asked twice, on two ladders, for two keys,
        // one of which nobody will ever make a room from.
        //
        // A *finished* one is not refused. It means that subgroup was made, and a
        // group is entitled to a second run by the same people -- which was
        // impossible while the ceremony ran in a room derived from its admins,
        // because asking again handed back the first ceremony's key and the "new"
        // subgroup was the old one renamed. Moving the ceremony into the parent's
        // room is what retired that, and this is the whole of what is left.
        val running = database.dkgSessionDao().getLatestSubgroupSessionFor(
            chatRoomId = parentChatRoomId,
            parentChatRoomId = parentChatRoomId,
            participantPublicKeys = DkgSession.formatParticipants(admins).orEmpty()
        )
        if (running != null &&
            running.stage != DkgRitualStage.FAILED &&
            running.stage != DkgRitualStage.COMPLETE
        ) {
            return "A key ceremony for a subgroup run by exactly these members is " +
                "already under way."
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

        // The certificate is a signature over the name, so it has to be the name
        // the room ends up with. `MarmotGroupCreation` normalises again on the
        // way in and `MarmotGroupName.of` is idempotent, so the two agree by
        // construction rather than by both callers remembering.
        val subgroupName = MarmotGroupName.of(name)

        // One certificate per child. A second is a `d`-tag replacement of the
        // first rather than a second subgroup, and spending a quorum's attention
        // to restate something they have already signed is worse than doing
        // nothing. Two coordinators racing still produce two -- neither can see
        // the other's session before it completes -- and `certificateFor` folds
        // those together; this only stops the case somebody can actually see.
        check(certificateFor(database, subgroupChatRoomId, parentChatRoomId) == null) {
            "$parentChatRoomId has already certified $subgroupChatRoomId"
        }

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
                name = subgroupName,
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
     * parent admins can press the button on the same admin set: the second's
     * `proposeRitual` folds into the ceremony the first opened, but both may go on
     * to propose a certificate and both sessions can complete.
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
     * Runs where the ceremony ran -- the parent's room -- because it has to:
     * `GroupKeyStateManager.propose` and `FrostSigningManager.signingPath` both tie
     * a key state to the room its ceremony was held in, and the room the state is
     * *about* does not exist yet.
     *
     * Signed by the **child's** quorum for all that, not the parent's. The room is
     * only the transport; who signs comes from the ceremony's host keys, and the
     * key it signs with is the one that ceremony produced. A parent admin outside
     * the subgroup reads the messages and holds no share to answer them with.
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
            lineage = SubgroupLineage.of(
                SubgroupParentage(
                    parentChatRoomId = parentChatRoomId,
                    certificate = certificate.toEvent()
                )
            )
        )
    }

    // ---- Adoption: a group that already exists gaining a parent -------------
    //
    // Two signings and no ceremony, which is the whole of why this is cheap: the
    // key exists, so there is no ChillDKG, and the room exists, so there is
    // nothing to create.
    //
    // ```
    //  1. birth certificate, in the adopting group -> its quorum signs the child's id
    //  2. key state, in the child itself           -> the child's quorum re-announces,
    //                                                 carrying every certificate it accepts
    // ```
    //
    // The order is forced, not chosen: the key state carries the certificate, so
    // the certificate has to exist first. Which means **the parent commits first
    // and the child decides second** -- step 1 is an offer, and step 2 is the
    // acceptance. Picking a parent is not choosing on the child's behalf.
    //
    // Both steps need somebody in both groups, and there is no way around it:
    // step 1 needs the child's threshold key, which only the child's members
    // hold, and step 2 needs the certificate, which `recordSignedEvents` files
    // only for the adopting group's members. They need not be the same person and
    // their capabilities differ. See docs/subgroups-multiple-parents.md.

    /**
     * Why [adoptingRoom] cannot offer to adopt [childChatRoomId], or null when it
     * can.
     *
     * Read by the picker so nothing is offered that could only fail, and again
     * before anything is published, because a screen not drawing something is not
     * a guard. Each message says what to do rather than what is wrong.
     *
     * "This device" throughout means **the profile in the chair**, and
     * `FrostSigningManager.canSign` does not currently tell the two apart -- see
     * the note under docs/subgroups-multiple-parents.md. These refusals are
     * honest about capability and silent about persona.
     */
    suspend fun refuseAdoption(
        database: MantraDatabase,
        adoptingRoom: LocalChatRoom,
        childChatRoomId: String,
        userPublicKey: HexKey
    ): String? {
        val adoptingChatRoomId = adoptingRoom.chatRoom.id

        // The certificate is signed by the adopting group's key, so a device
        // holding no share of it cannot open the session at all --
        // `proposeSigningBatch` throws outright, and throwing at a button is not
        // a UI. First, because it is the one refusal the user cannot fix by
        // picking differently.
        if (!FrostSigningManager.canSign(database, adoptingChatRoomId)) {
            return "Only a member who holds a share of that group's key can adopt a group into it."
        }

        // Read off the room's own `MarmotGroupData` rather than off
        // `Participant.adminAt`, for the reason `refuseSubgroup` gives: that
        // column is written only by `processGroupMembershipChanges` on an
        // arriving commit, so on a room this device created it is null for
        // everybody, including the creator.
        val adoptingAdmins = runCatching {
            adoptingRoom.chatRoom.toMlsGroup()?.currentMarmotData()?.adminPubkeys
        }.getOrNull()
            ?: return "A group can only adopt another if it has admins."

        if (!adoptingAdmins.contains(userPublicKey)) {
            return "Only an admin of that group can adopt a group into it."
        }

        if (adoptingChatRoomId.equals(childChatRoomId, ignoreCase = true)) {
            return "A group cannot be its own parent."
        }

        // The certificate carries the child's threshold key, and without it
        // `certifies` fails its fourth question on every device including this
        // one. That key lives on the child's own key state, which only the
        // child's members hold -- so this is the overlap requirement showing up
        // as a refusal.
        val child = database.groupKeyStateDao().getLocalByChatRoomId(childChatRoomId)
            ?: return "This group has not agreed what it signs with, so there is nothing to certify."

        if (child.lineage()?.names(adoptingChatRoomId) == true) {
            return "That group has already adopted this one."
        }

        // One certificate per child per parent. A second is a `d`-tag replacement
        // of the first rather than a second adoption, and spending a quorum's
        // attention to restate something they have already signed is worse than
        // doing nothing.
        if (certificateFor(database, childChatRoomId, adoptingChatRoomId) != null) {
            return "That group has already offered to adopt this one."
        }

        // A cycle, as far as this device can see -- and only as far as that. The
        // walk is bounded and visited-set guarded because a lineage is a DAG now
        // rather than a tree: with one parent nothing was ever tempted to recurse.
        if (isAncestorOf(database, childChatRoomId, adoptingChatRoomId)) {
            return "That group is already a subgroup of this one."
        }

        return null
    }

    /**
     * Whether [candidate] is [chatRoomId] or anything beneath it, as far as this
     * device's own verified lineages reach.
     *
     * Bounded and visited-set guarded. A cycle is constructible -- two quorums
     * cooperating in something pointless -- and more to the point a lineage is a
     * DAG now, so the fan-out makes an unbounded walk worth refusing to write.
     *
     * Advisory. A device sees only the lineages it holds states for, so a false
     * answer here is "I cannot see one", never "there is none". The real
     * protection is that an adoption needs the child's own quorum.
     */
    private suspend fun isAncestorOf(
        database: MantraDatabase,
        chatRoomId: String,
        candidate: String,
        depth: Int = MAXIMUM_LINEAGE_DEPTH
    ): Boolean {
        val seen = mutableSetOf(chatRoomId.lowercase())
        var frontier = listOf(chatRoomId)

        repeat(depth) {
            if (frontier.isEmpty()) return false

            val children = frontier.flatMap { parent ->
                database.groupKeyStateDao().getByParentChatRoomId(parent)
                    .map { it.keyState.chatRoomId }
            }

            if (children.any { it.equals(candidate, ignoreCase = true) }) return true

            frontier = children.filter { seen.add(it.lowercase()) }
        }

        return false
    }

    /**
     * Groups that have offered to adopt [chatRoomId] and are not its parents yet,
     * newest offer first.
     *
     * Read off every certificate this device holds, in any room, rather than off
     * the room this one is -- a certificate about this group is signed in the
     * *other* group's room, so it is filed against that room and would never be
     * found by asking this one. Each is checked by [classify], which verifies the
     * signature against the group it names and needs no lookup to do it.
     *
     * Already-parents are dropped, because an offer that has been accepted is
     * not an offer any more; the lineage is where that shows.
     */
    suspend fun offersTo(database: MantraDatabase, chatRoomId: String): List<HexKey> {
        val accepted = parentsOf(database, chatRoomId).map { it.lowercase() }.toSet()

        return database.groupSignedEventDao()
            .getByKind(SubgroupBirthCertificateEvent.KIND)
            .sortedByDescending { it.createdAt }
            .mapNotNull { signed ->
                (classify(chatRoomId, signed.toEvent())
                    as? ArrivingCertificate.OfferToUs)?.parentChatRoomId
            }
            .filterNot { it.lowercase() in accepted }
            .distinctBy { it.lowercase() }
    }

    /**
     * The groups this profile could offer [childChatRoomId] to, in room order.
     *
     * **The active profile's own rooms, and it cannot be anything else.** A
     * certificate is proposed *inside* the adopting group, so a candidate has to
     * be a room the key in the chair can already act in. Seeded from
     * `getChatRoomListByUserPublicKey`, which is keyed by identity: since several
     * profiles landed a device can hold rooms it must not offer here, because the
     * key that would have to sign is not the one acting.
     *
     * A group cannot be offered as a parent by somebody who is not in it. That is
     * the overlap requirement showing up as a list length, and an empty answer is
     * a fact about this member rather than a failure -- the remedy is a person,
     * not a button.
     *
     * Filtered through [refuseAdoption] itself rather than through a copy of its
     * rules, so the picker and the manager cannot drift. The cost is one MLS
     * decode per room, to reach `adminPubkeys`, plus two indexed reads; call it
     * off the main thread.
     */
    suspend fun adoptingCandidatesFor(
        database: MantraDatabase,
        childChatRoomId: String,
        userPublicKey: HexKey
    ): List<LocalChatRoom> =
        database.chatRoomDao().getChatRoomListByUserPublicKey(userPublicKey)
            .filter { candidate ->
                refuseAdoption(
                    database = database,
                    adoptingRoom = candidate,
                    childChatRoomId = childChatRoomId,
                    userPublicKey = userPublicKey
                ) == null
            }

    /**
     * Step 1: asks [adoptingRoom]'s quorum to certify an existing group as its
     * child.
     *
     * The same event kind, the same six checks on the other side, and the same
     * room it has always been proposed in -- what differs from founding is only
     * that the child is already there, so its id and key are read off its key
     * state rather than derived from a ceremony this device may not hold.
     *
     * **The roster and the name are the child's current ones**, and that is the
     * honest value rather than a compromise. On a founding certificate they are
     * the founding roster, frozen, because there is nothing else they could be.
     * Here the adopting group's admins are being asked about a group that exists,
     * and what they can weigh is who runs it now and what it is called now. They
     * are still frozen the moment they are signed, and still never a live roster:
     * `certifies` does not check either, for the reason it never did.
     */
    suspend fun proposeAdoption(
        database: MantraDatabase,
        adoptingRoom: LocalChatRoom,
        childRoom: LocalChatRoom,
        userPublicKey: HexKey
    ): FrostSigningSession {
        val adoptingChatRoomId = adoptingRoom.chatRoom.id
        val childChatRoomId = childRoom.chatRoom.id

        refuseAdoption(database, adoptingRoom, childChatRoomId, userPublicKey)?.let {
            throw IllegalStateException(it)
        }

        val child = database.groupKeyStateDao().getLocalByChatRoomId(childChatRoomId)
            ?: throw IllegalStateException("$childChatRoomId has agreed no key state to certify")

        val path = child.keyState.pathIndices()
            ?: throw IllegalStateException(
                "$childChatRoomId names ${child.keyState.derivationPath}, which is not a path"
            )

        val childAdmins = runCatching {
            childRoom.chatRoom.toMlsGroup()?.currentMarmotData()?.adminPubkeys
        }.getOrNull().orEmpty()

        val childName = MarmotGroupName.of(childRoom.chatRoom.subject.orEmpty().ifBlank { "a group" })

        logger.i("Asking $adoptingChatRoomId to adopt $childChatRoomId as its subgroup")

        return FrostSigningManager.proposeSigning(
            database = database,
            localChatRoom = adoptingRoom,
            userPublicKey = userPublicKey,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = childChatRoomId,
                parentChatRoomId = adoptingChatRoomId,
                thresholdPublicKey = child.keyState.thresholdPublicKey,
                adminPublicKeys = childAdmins,
                name = childName,
                path = path
            ),
            content = childChatRoomId
        )
    }

    /**
     * Step 2: asks the child's own quorum to accept [parentChatRoomId], by
     * re-announcing its key state with that parentage added.
     *
     * The acceptance, and the only half of an adoption that decides anything. The
     * certificate is an offer until this is signed, which is why
     * `ProposedEvent`'s 30326 arm leads with the parents -- this proposal is
     * where a member of the child answers it.
     *
     * **Carries the resulting set, not the addition.** A key state is replaceable
     * and the newest wins wholesale, so a state naming only the new parent would
     * silently drop every existing one. The current lineage is read off the row
     * here rather than taken from the caller, and `SubgroupLineage.plus` is what
     * makes adding an addition.
     *
     * Signed in the child's **own** room, which it can be because the room
     * exists: `GroupKeyStateManager.propose`'s first branch holds, and
     * `FrostSigningManager.signingPath` resolves off the child's own key state.
     * Founding could not do either, which is why its key state is signed where
     * its ceremony ran.
     */
    suspend fun proposeAcceptance(
        database: MantraDatabase,
        childRoom: LocalChatRoom,
        userPublicKey: HexKey,
        parentChatRoomId: HexKey
    ): FrostSigningSession {
        val childChatRoomId = childRoom.chatRoom.id

        val child = database.groupKeyStateDao().getLocalByChatRoomId(childChatRoomId)
            ?: throw IllegalStateException(
                "$childChatRoomId has agreed no key state, so it has none to re-announce"
            )

        val path = child.keyState.pathIndices()
            ?: throw IllegalStateException(
                "$childChatRoomId names ${child.keyState.derivationPath}, which is not a path"
            )

        // The certificate this device holds for the pair. Only a member of the
        // adopting group has one -- `recordSignedEvents` files it against that
        // group's room -- so this is the overlap requirement again, from the
        // other side.
        val certificate = certificateFor(database, childChatRoomId, parentChatRoomId)
            ?: throw IllegalStateException(
                "$parentChatRoomId has not certified $childChatRoomId, so there is nothing to accept"
            )

        val parentage = SubgroupParentage(
            parentChatRoomId = parentChatRoomId,
            certificate = certificate.toEvent()
        )

        val lineage = child.lineage()?.plus(parentage) ?: SubgroupLineage.of(parentage)
        checkNotNull(lineage) { "$childChatRoomId already names $parentChatRoomId as a parent" }

        val key = FrostSigningManager.completedKey(database, childChatRoomId)
            ?: throw IllegalStateException(
                "This device holds no share of $childChatRoomId's key to propose with"
            )

        logger.i(
            "Asking $childChatRoomId to accept $parentChatRoomId, making " +
                "${lineage.size} parents in all"
        )

        return GroupKeyStateManager.propose(
            database = database,
            localChatRoom = childRoom,
            userPublicKey = userPublicKey,
            key = key,
            path = path,
            lineage = lineage
        )
    }

    /**
     * Asks the child's own quorum to stop claiming [parentChatRoomId], by
     * re-announcing its key state without that parentage.
     *
     * The same act as [proposeAcceptance] with the set the other way, and the
     * reason `SubgroupLineage.minus` exists. It **revokes nothing**: a certificate
     * is a signature and exists forever, and what this removes is the child's half
     * of the link -- which is the half that decides, since a lineage is what the
     * child's quorum most recently declared.
     *
     * The parent's own list does not follow. `subgroupsOf` reads that group's
     * certificates, so it will go on naming this room; a parent device that also
     * holds this room's key state can see that it no longer names them, and one
     * that does not cannot and must not guess.
     */
    suspend fun proposeDisavowal(
        database: MantraDatabase,
        childRoom: LocalChatRoom,
        userPublicKey: HexKey,
        parentChatRoomId: HexKey
    ): FrostSigningSession {
        val childChatRoomId = childRoom.chatRoom.id

        val child = database.groupKeyStateDao().getLocalByChatRoomId(childChatRoomId)
            ?: throw IllegalStateException(
                "$childChatRoomId has agreed no key state, so it has none to re-announce"
            )

        val path = child.keyState.pathIndices()
            ?: throw IllegalStateException(
                "$childChatRoomId names ${child.keyState.derivationPath}, which is not a path"
            )

        val current = child.lineage()
        check(current?.names(parentChatRoomId) == true) {
            "$childChatRoomId does not name $parentChatRoomId as a parent"
        }

        val key = FrostSigningManager.completedKey(database, childChatRoomId)
            ?: throw IllegalStateException(
                "This device holds no share of $childChatRoomId's key to propose with"
            )

        val remaining = current.minus(parentChatRoomId)

        logger.i(
            "Asking $childChatRoomId to stop claiming $parentChatRoomId, leaving " +
                "${remaining?.size ?: 0} parents"
        )

        return GroupKeyStateManager.propose(
            database = database,
            localChatRoom = childRoom,
            userPublicKey = userPublicKey,
            key = key,
            path = path,
            lineage = remaining
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
     * Every group that has certified a room as its child, verified, in canonical
     * order -- empty for a room that is nobody's child.
     *
     * Off `GroupKeyStateParent`, whose rows exist only where the certificate was
     * checked against that parent's own signature -- see
     * `GroupKeyStateManager.stateFrom`, which drops the whole state rather than
     * keeping a lineage it could not check. So every id here is a group that
     * really did certify this room.
     *
     * More than one is the ordinary case now, not a corner: a group is founded
     * with one parent and may be adopted by another later. The order is the
     * lineage's own and says nothing about which came first.
     *
     * A room whose state this device does not hold answers empty even when it
     * really is a subgroup, which is the position a member welcomed in after the
     * founding is in. Nothing unverified may be written here.
     */
    suspend fun parentsOf(database: MantraDatabase, chatRoomId: String): List<HexKey> =
        database.groupKeyStateDao().getLocalByChatRoomId(chatRoomId)?.parentChatRoomIds()
            .orEmpty()

    /**
     * A group this room is a child of, plus the room itself where this device
     * has it.
     *
     * [room] is the only place a parent's *name* can come from: a certificate
     * names the child, never the certifier, so a device that is not in the
     * parent room knows the lineage and not what to call it. Null is therefore
     * the ordinary case for a subgroup member who is not also a parent member,
     * and the id is all such a screen can honestly show.
     */
    data class Parent(
        val chatRoomId: HexKey,
        val room: LocalChatRoom?,
    )

    /**
     * [parentsOf], with each parent's room where this device holds one.
     *
     * The same verified set in the same order -- this only looks each id up, and
     * a parent whose room is missing stays in the list rather than being dropped.
     */
    suspend fun parentGroupsOf(database: MantraDatabase, chatRoomId: String): List<Parent> =
        parentsOf(database, chatRoomId).map { parentChatRoomId ->
            Parent(
                chatRoomId = parentChatRoomId,
                room = database.chatRoomDao().findChatRoomById(parentChatRoomId)
            )
        }

    /**
     * What a certificate arriving in a room is, or null if it is nothing this
     * room should believe.
     *
     * Pure, and the decision the 30329 dispatch arm cannot make for itself. Two
     * true things a certificate can be, and the room it arrived in is what tells
     * them apart:
     *
     *  - [OurChild] -- **this** room signed it, about another room. The founding
     *    case, and the one that was always here.
     *  - [OfferToUs] -- it is about **this** room, signed by the group it names.
     *    A parent offering to adopt an existing group, which is the half of an
     *    adoption the child's members would otherwise never hear about until a
     *    proposal asked for their signature.
     *
     * Both are fully verified before they are returned, and neither needs a
     * lookup: `certifies` checks a signature against a room id, and a room id is
     * a public key. So an offer can be believed on its own merits by a device that
     * has never held the offering group's room.
     *
     * **Neither is stored as the offering group's event.** `GroupSignedEvent`
     * declares a foreign key onto `ChatRoom`, so a child device with no row for
     * the parent could not file one if it wanted to. It does not need to: the
     * certificate reaches the child's devices for real inside the key state the
     * child's own quorum signs, and what an offer buys here is notice, not
     * evidence.
     */
    sealed interface ArrivingCertificate {
        /** This room certified [subgroupChatRoomId] as its child. */
        data class OurChild(val subgroupChatRoomId: String) : ArrivingCertificate

        /** [parentChatRoomId] has certified this room as *its* child. */
        data class OfferToUs(val parentChatRoomId: HexKey) : ArrivingCertificate
    }

    fun classify(chatRoomId: String, innerEvent: Event): ArrivingCertificate? {
        val subgroupChatRoomId =
            SubgroupBirthCertificateEvent.parseSubgroupChatRoomId(innerEvent.tags)
                ?: return null

        // A certificate names a parent or it is not one; `certifies` would refuse
        // it either way, so reading the tag first costs nothing and lets the next
        // check come before anything else.
        val parentChatRoomId =
            SubgroupBirthCertificateEvent.parseParentChatRoomId(innerEvent.tags) ?: return null

        // **A group is not its own child**, and this is the one self-referential
        // certificate `certifies` waves through: a group can sign, truthfully by
        // every check that function makes, that it is its own parent -- the
        // author, the subject and the named parent are one room, the id derives
        // from the key, and the signature verifies. It means nothing, and it has
        // to be refused before either branch below rather than inside one, or the
        // first branch reads it as an ordinary child and the group appears in its
        // own list of subgroups.
        if (subgroupChatRoomId.equals(parentChatRoomId, ignoreCase = true)) return null

        if (SubgroupBirthCertificateEvent.certifies(innerEvent, subgroupChatRoomId, chatRoomId)) {
            return ArrivingCertificate.OurChild(subgroupChatRoomId)
        }

        // Only a certificate about *this* room can be an offer to it. Checked
        // before the signature so that a certificate about some third room,
        // signed by some fourth, cannot be read as anything at all -- it is
        // nobody's business in this room whoever signed it.
        if (!subgroupChatRoomId.equals(chatRoomId, ignoreCase = true)) return null

        if (!SubgroupBirthCertificateEvent.certifies(innerEvent, chatRoomId, parentChatRoomId)) {
            return null
        }

        return ArrivingCertificate.OfferToUs(parentChatRoomId)
    }

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
