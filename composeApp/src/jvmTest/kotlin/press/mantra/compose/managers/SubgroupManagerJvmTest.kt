package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import press.mantra.compose.nostr.Relays
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent

/**
 * What a parent's own record says about its children, and what it refuses to say.
 *
 * There is no table of subgroups. The certificates *are* the record -- the
 * group's own signed statement, checkable without a lookup, and held by every
 * device that followed the signing session rather than only by the signers. So
 * everything a parent knows about its children is a read over
 * `GroupSignedEvent`, and these are the properties that read has to have.
 *
 * The negative cases carry the weight, as they do for chronicles. A list built
 * from signed events is only as safe as its filter: a member can put a rumor of
 * this kind into the room, and a real certificate signed by some *other* group is
 * a valid signature over a true-looking statement with no standing behind it.
 * Neither may reach the list.
 *
 * The one positive case that matters as much is a certified child whose room this
 * device does not have. That is the normal position of a parent member who is not
 * in the subgroup, and of everybody between the certificate being signed and the
 * room being created -- so a list that could only show rooms would be empty
 * exactly when it is most needed.
 */
class SubgroupManagerJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val participants = 3
    private val threshold = 2

    /** The parent group, which does the certifying. */
    private val parentMaterial: KeyMaterial = keyMaterial(
        "1c0ffee0000000000000000000000000000000000000000000000000000000a1"
    )

    /** The child. Its own ceremony, its own key, its own room. */
    private val childMaterial: KeyMaterial = keyMaterial(
        "2bada550000000000000000000000000000000000000000000000000000000b2"
    )

    /** A second child, so "one entry per child" can be told from "one entry". */
    private val siblingMaterial: KeyMaterial = keyMaterial(
        "3decade0000000000000000000000000000000000000000000000000000000c3"
    )

    /** A group with no standing here at all. */
    private val strangerMaterial: KeyMaterial = keyMaterial(
        "4facade0000000000000000000000000000000000000000000000000000000d4"
    )

    private val path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH

    private val parentRoomId = roomOf(parentMaterial)
    private val childRoomId = roomOf(childMaterial)
    private val siblingRoomId = roomOf(siblingMaterial)

    private val user = KeyPair().pubKey.toHexKey()
    private val alice = KeyPair().pubKey.toHexKey()
    private val bob = KeyPair().pubKey.toHexKey()
    private val carol = KeyPair().pubKey.toHexKey()

    @Test
    fun `a parent lists a child it certified, room or no room`(): Unit = runBlocking {
        seedRoom(parentRoomId)
        record(certificate())

        val subgroups = SubgroupManager.subgroupsOf(db, parentRoomId)

        assertEquals(1, subgroups.size)
        val subgroup = subgroups.single()

        assertEquals(childRoomId, subgroup.chatRoomId)
        assertEquals(parentRoomId, subgroup.parentChatRoomId)
        assertEquals("Translation team", subgroup.name)
        assertEquals(listOf(user, alice, bob), subgroup.adminPublicKeys)

        // The whole point of reading certificates rather than rooms: this device
        // holds no room for the child and knows about it anyway. That is the
        // position of every parent member who is not in the subgroup.
        assertNull(subgroup.room)
        assertFalse(subgroup.isJoined)
        assertNull(subgroup.keyState)
    }

    @Test
    fun `a child whose room this device holds reports it`(): Unit = runBlocking {
        seedRoom(parentRoomId)
        seedRoom(childRoomId)
        record(certificate())

        val subgroup = SubgroupManager.subgroupsOf(db, parentRoomId).single()

        assertTrue(subgroup.isJoined)
        assertEquals(childRoomId, subgroup.room?.chatRoom?.id)
    }

    @Test
    fun `a certificate signed by another group is not listed`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        // A real quorum and a real signature by a group that has no standing to
        // say anything about this parent's children. Filed in the parent's room
        // -- which a member can cause -- and refused on read.
        record(certificate(signer = strangerMaterial, author = strangerMaterial))

        assertTrue(SubgroupManager.subgroupsOf(db, parentRoomId).isEmpty())
        assertNull(SubgroupManager.certificateFor(db, childRoomId, parentRoomId))
    }

    @Test
    fun `a certificate nobody signed is not listed`(): Unit = runBlocking {
        seedRoom(parentRoomId)
        record(certificate(signature = "f".repeat(128)))

        assertTrue(SubgroupManager.subgroupsOf(db, parentRoomId).isEmpty())
    }

    @Test
    fun `a certificate whose id does not derive from the key it names is not listed`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        record(
            certificate(
                tags = SubgroupBirthCertificateEvent.assembleTags(
                    subgroupChatRoomId = childRoomId,
                    parentChatRoomId = parentRoomId,
                    thresholdPublicKey = strangerMaterial.thresholdPublicKey.value.toHex(),
                    adminPublicKeys = listOf(user, alice, bob),
                    name = "Translation team",
                    path = path
                )
            )
        )

        assertTrue(SubgroupManager.subgroupsOf(db, parentRoomId).isEmpty())
    }

    @Test
    fun `two certificates for one child collapse to the newest`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        // Two parent admins pressing the button on the same admin set. Both
        // sessions can complete and both rows coexist, because GroupSignedEvent is
        // keyed on the event id -- and both say the same true thing, so which one
        // wins does not matter. What must not happen is two entries.
        record(certificate(createdAt = 1_700_000_000, name = "First name"))
        record(certificate(createdAt = 1_700_000_900, name = "Second name"))

        val subgroups = SubgroupManager.subgroupsOf(db, parentRoomId)

        assertEquals(1, subgroups.size)
        assertEquals("Second name", subgroups.single().name)
        assertEquals(
            "Second name",
            SubgroupBirthCertificateEvent.parseName(
                assertNotNull(SubgroupManager.certificateFor(db, childRoomId, parentRoomId)).tags
            )
        )
    }

    @Test
    fun `two children are two entries, newest first`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        record(certificate(createdAt = 1_700_000_000, name = "Older"))
        record(
            certificate(
                createdAt = 1_700_000_900,
                name = "Newer",
                subgroupMaterial = siblingMaterial
            )
        )

        assertEquals(
            listOf(siblingRoomId, childRoomId),
            SubgroupManager.subgroupsOf(db, parentRoomId).map { it.chatRoomId }
        )
    }

    @Test
    fun `record refuses a certificate the room did not sign`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        // The path a member takes to forge one: send a rumor of kind 30329 into
        // the room and let the inbound arm file it. `record` is reached with the
        // same arguments a completed session would use and cannot tell the two
        // apart, so it checks.
        val forged = certificate(signer = strangerMaterial, author = strangerMaterial)
        record(forged)

        assertNull(SubgroupManager.record(db, parentRoomId, forged))
        assertNotNull(SubgroupManager.record(db, parentRoomId, certificate().also { record(it) }))
    }

    @Test
    fun `record refuses a certificate that names no subgroup`(): Unit = runBlocking {
        seedRoom(parentRoomId)

        val nameless = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = childRoomId,
                parentChatRoomId = parentRoomId,
                thresholdPublicKey = childMaterial.thresholdPublicKey.value.toHex(),
                adminPublicKeys = listOf(user),
                name = "Translation team",
                path = path
            ).filterNot { it[0] == "d" }.toTypedArray()
        )

        assertNull(SubgroupManager.record(db, parentRoomId, nameless))
    }

    // ---- the refusals ------------------------------------------------------

    @Test
    fun `a member holding no share of the parent's key is refused`(): Unit = runBlocking {
        // Not a permission rule: `proposeSigningBatch` throws outright for a
        // device with no share, so without this the button would crash rather
        // than decline. It is also the one refusal the user cannot fix by picking
        // differently, which is why it is checked first.
        val parent = parentWith(listOf(alice, bob, carol), canSign = false)

        assertNotNull(refusal(parent, setOf(alice, bob)))
    }

    @Test
    fun `a member who is not an admin of the parent is refused`(): Unit = runBlocking {
        val parent = parentWith(listOf(alice, bob, carol), isAdmin = false)

        assertNotNull(refusal(parent, setOf(alice, bob)))
    }

    @Test
    fun `an admin is read off the group's data, not off a flag no creator ever sets`(): Unit =
        runBlocking {
            // The bug this replaced. `Participant.adminAt` is written in exactly
            // one place -- `processGroupMembershipChanges`, on an arriving commit
            // -- and a device that created a room has processed none, so every
            // row reads as a non-admin including the creator's. A guard on that
            // column refused the one person certain to be an admin.
            //
            // MarmotGroupData is right from epoch 0 on both sides, so this passes
            // with every adminAt still null.
            val parent = parentWith(listOf(alice, bob, carol))

            assertTrue(
                parent.localParticipants.all { it.participant.adminAt == null },
                "the fixture must not set the flag, or it is not testing the fix",
            )
            assertNull(refusal(parent, setOf(alice, bob)))
        }

    @Test
    fun `a group with no MLS state has no admin list to consult`(): Unit = runBlocking {
        // A NIP-17 room: everybody is an equal member and there is no group
        // context to put a certificate in. The button is hidden there for the
        // same reason, and this is the guard behind it.
        (listOf(alice, bob, carol) + user).forEach { seedProfile(it) }
        val nip17 = assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = listOf(alice, bob, carol),
                subject = "Ekklesia",
            )
        )

        assertNotNull(refusal(nip17, setOf(alice, bob)))
    }

    @Test
    fun `a subgroup of fewer than three admins is refused`(): Unit = runBlocking {
        val parent = parentWith(listOf(alice, bob, carol))

        assertNotNull(refusal(parent, setOf(alice)))
        assertNull(refusal(parent, setOf(alice, bob)), "the coordinator is the third")
    }

    @Test
    fun `a subgroup containing somebody who is not in the group is refused`(): Unit = runBlocking {
        val parent = parentWith(listOf(alice, bob))
        val stranger = KeyPair().pubKey.toHexKey()

        assertNotNull(refusal(parent, setOf(alice, stranger)))
    }

    @Test
    fun `a subgroup may be the whole group`(): Unit = runBlocking {
        // A subgroup is a logical division -- a group deciding that some of its
        // work belongs to a differently-keyed room -- not a smaller membership.
        // "Everybody" is a normal answer.
        //
        // This used to be refused on set size, which was wrong twice: it turned
        // down a legitimate subgroup, and it did so on a proxy for a collision
        // that a parent whose membership has changed would not even have.
        val parent = parentWith(listOf(alice, bob))

        assertNull(refusal(parent, setOf(alice, bob)))
    }

    @Test
    fun `the group's own ceremony does not block a subgroup in the same room`(): Unit =
        runBlocking {
            // The collision the size rule was standing in for, made concrete. A
            // subgroup over everybody derives the room the group's own ceremony was
            // held in -- so the room is shared, and only `parentChatRoomId` keeps
            // the two ceremonies apart. Without that scoping `proposeRitual` would
            // hand back the *group's* key and quietly make the child the parent.
            val parent = parentWith(listOf(alice, bob))
            val ceremonyRoomId = SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob), user)

            seedCeremonyRoom(ceremonyRoomId, listOf(alice, bob, user))
            db.dkgSessionDao().upsert(ownCeremony(ceremonyRoomId))

            assertNull(
                refusal(parent, setOf(alice, bob)),
                "the group's own ceremony is not this subgroup's",
            )
        }

    @Test
    fun `the same parent cannot have two subgroups over the same admins`(): Unit = runBlocking {
        val parent = parentWith(listOf(alice, bob, carol))

        assertNull(refusal(parent, setOf(alice, bob)))

        // The one collision that survives, and it is degenerate: same parent,
        // same admins. The ceremony room is derived from the admins and the
        // ceremonies in it are told apart by their parent, so this pair has
        // nothing left to distinguish them -- which is another way of saying it
        // is one subgroup asked for twice.
        val ceremonyRoomId = SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob), user)
        seedCeremonyRoom(ceremonyRoomId, listOf(alice, bob, user))
        db.dkgSessionDao().upsert(
            ownCeremony(ceremonyRoomId).copy(id = "s1", parentChatRoomId = parentRoomId)
        )

        assertNotNull(refusal(parent, setOf(alice, bob)))

        // And a different admin set is a different room, so it is untouched.
        assertNull(refusal(parent, setOf(alice, carol)))
    }

    @Test
    fun `the ceremony room is the same whichever way the admins are ordered`() {
        // Every device has to land on the same room without being told, which is
        // what makes a ceremony findable at all -- and what makes two members
        // starting the same subgroup meet rather than fork.
        assertEquals(
            SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob), user),
            SubgroupManager.ceremonyRoomIdFor(setOf(bob, alice), user)
        )
        // And the coordinator is in it whether or not they were ticked.
        assertEquals(
            SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob), user),
            SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob, user), user)
        )
    }

    @Test
    fun `certifying a child the parent has already certified is refused`(): Unit = runBlocking {
        seedRoom(parentRoomId)
        record(certificate())

        val parentRoom = assertNotNull(db.chatRoomDao().findChatRoomById(parentRoomId))

        // One certificate per child: a second is a `d`-tag replacement rather
        // than a second subgroup, and spending a quorum's attention to restate
        // something they have already signed is worse than doing nothing.
        //
        // Two coordinators racing still produce two -- neither can see the
        // other's session before it completes -- which is why `certificateFor`
        // folds them. This only stops the case somebody can actually see.
        assertFailsWith<IllegalStateException> {
            SubgroupManager.proposeBirthCertificate(
                database = db,
                parentRoom = parentRoom,
                userPublicKey = user,
                key = completedChildCeremony(),
                adminPublicKeys = listOf(user, alice, bob),
                name = "Translation team",
            )
        }
    }

    @Test
    fun `the name the parent is asked to sign is the name the room will carry`(): Unit =
        runBlocking {
            // A parent that can actually sign: the proposal has to get as far as
            // writing the event whose tags are what this is about.
            val parentRoom = parentWith(listOf(alice, bob))

            val session = SubgroupManager.proposeBirthCertificate(
                database = db,
                parentRoom = parentRoom,
                userPublicKey = user,
                key = completedChildCeremony(),
                adminPublicKeys = listOf(user, alice, bob),
                // Bare, as it comes off the ceremony room's subject -- that room
                // is NIP-17 and keeps the name unmarked.
                name = "Translation team",
            )

            val proposed = Event.fromJson(
                assertNotNull(db.frostSigningSessionDao().getItems(session.id).firstOrNull())
                    .unsignedEventJson
            )

            // A quorum is being asked to sign a name, and `MarmotGroupCreation`
            // will name the room by the same rule from the same subject. If only
            // one of the two applied it, the parent would certify "Translation
            // team" and the room would be called "#Translation team".
            assertEquals(
                MarmotGroupName.of("Translation team"),
                SubgroupBirthCertificateEvent.parseName(proposed.tags)
            )
        }

    @Test
    fun `an unnamed subgroup cannot be certified`(): Unit = runBlocking {
        seedRoom(parentRoomId)
        val parentRoom = assertNotNull(db.chatRoomDao().findChatRoomById(parentRoomId))

        // The parent's admins would be approving a hash. The name is what makes
        // the proposal legible, so it is required rather than defaulted.
        assertFailsWith<IllegalArgumentException> {
            SubgroupManager.proposeBirthCertificate(
                database = db,
                parentRoom = parentRoom,
                userPublicKey = user,
                key = completedChildCeremony(),
                adminPublicKeys = listOf(user, alice, bob),
                name = "  ",
            )
        }
    }

    // ---- fixtures ----------------------------------------------------------

    /**
     * The NIP-17 room a ceremony runs in, stood up the way the picker does it --
     * room first, because `DkgSession.chatRoomId` is a foreign key onto it.
     */
    private suspend fun seedCeremonyRoom(expectedId: String, members: List<String>) {
        members.forEach { seedProfile(it) }

        val room = assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = members.filterNot { it == user },
                subject = "Translation team",
            )
        )

        assertEquals(
            expectedId,
            room.chatRoom.id,
            "the derived ceremony room is the one createNip17ChatRoom lands on",
        )
    }

    /** A ceremony held in [chatRoomId] for no subgroup: the group's own. */
    private fun ownCeremony(chatRoomId: String) = DkgSession(
        id = "own-ceremony",
        chatRoomId = chatRoomId,
        coordinatorPublicKey = user,
        userPublicKey = user,
        threshold = 2,
        participantCount = 3,
        hostPublicKey = user,
        round1Random = "aa".repeat(32),
        round2AuxRandom = "bb".repeat(32),
    )

    /** A finished ceremony for the child, as far as these guards can tell. */
    private fun completedChildCeremony() = DkgSession(
        id = "child-ceremony",
        chatRoomId = parentRoomId,
        coordinatorPublicKey = user,
        userPublicKey = user,
        threshold = 2,
        participantCount = 3,
        stage = DkgRitualStage.COMPLETE,
        hostPublicKey = user,
        round1Random = "aa".repeat(32),
        round2AuxRandom = "bb".repeat(32),
        thresholdPublicKey = childMaterial.thresholdPublicKey.value.toHex(),
        secretShare = "dd".repeat(32),
    )


    private fun keyMaterial(secret: String): KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(ByteVector32(secret)),
        nParticipants = participants,
        threshold = threshold
    )

    private fun roomOf(material: KeyMaterial): String =
        SharedKeyDerivation.marmotGroupId(material.thresholdPublicKey.value.toHex(), path)

    private suspend fun refusal(parent: LocalChatRoom, admins: Set<String>): String? =
        SubgroupManager.refuseCeremonyRoom(
            database = db,
            parentRoom = parent,
            adminPublicKeys = admins,
            coordinatorPublicKey = user
        )

    /**
     * A parent this device could actually make a subgroup of: an admin of it, and
     * holding a share of its key.
     *
     * Both are conditions of `refuseCeremonyRoom` before it looks at the picking
     * at all, so a fixture without them tests only the first refusal. [canSign]
     * and [isAdmin] are here so the tests that are *about* those two can turn
     * each off on its own.
     */
    /**
     * A parent this device could actually make a subgroup of.
     *
     * Built as what a real parent *is* -- the admin room derived from the
     * group's own key -- rather than as a NIP-17 room with MLS state bolted on.
     * That is not fussiness: `ChatRoom.deriveChatRoomId` returns a 33-byte
     * compressed key (66 hex) and `MarmotGroupData.nostrGroupId` takes 32 bytes,
     * so a NIP-17 id put in that field produces an extension `currentMarmotData()`
     * reads back as **null**, silently. The fixture that did it tested nothing but
     * that null.
     *
     * [canSign] and [isAdmin] are switchable so the tests about those two can turn
     * each off on its own; both are conditions `refuseCeremonyRoom` checks before
     * it looks at the picking at all.
     */
    private suspend fun parentWith(
        members: List<String>,
        canSign: Boolean = true,
        isAdmin: Boolean = true,
    ): LocalChatRoom {
        (members + user).forEach { seedProfile(it) }

        val admins = if (isAdmin) members + user else members

        val metadata = MarmotGroupData(
            nostrGroupId = parentRoomId,
            name = "Ekklesia",
            description = SharedKeyDerivation.describe("Admins of Ekklesia."),
            adminPubkeys = admins,
            relays = Relays.DefaultDMRelayList.map { it.url }
        )
        val mlsGroup = MlsGroup.create(
            KeyPair().pubKey,
            Ed25519.generateKeyPair().privateKey,
            listOf(metadata.toExtension())
        )

        db.chatRoomDao().upsert(
            ChatRoom(
                id = parentRoomId,
                userPublicKey = user,
                subject = "Ekklesia",
                description = metadata.description,
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            )
        )

        // Deliberately with `adminAt` left null on every one of them. That column
        // is only ever written by `processGroupMembershipChanges`, on an arriving
        // commit, so on a device that made the room it is null for everybody --
        // which is the state the guard has to work in.
        db.participantDao().upsert(
            (members + user).map {
                Participant(
                    participantPublicKey = it,
                    chatRoomId = parentRoomId,
                    relayHint = null,
                )
            }
        )

        if (canSign) {
            // `completedKey` rederives each key-holding ceremony's room and
            // matches, which is exactly how a real admin room resolves its key.
            db.dkgSessionDao().upsert(
                DkgSession(
                    id = "parent-ceremony",
                    chatRoomId = parentRoomId,
                    coordinatorPublicKey = user,
                    userPublicKey = user,
                    threshold = 2,
                    participantCount = members.size + 1,
                    stage = DkgRitualStage.COMPLETE,
                    hostPublicKey = user,
                    round1Random = "aa".repeat(32),
                    round2AuxRandom = "bb".repeat(32),
                    thresholdPublicKey = parentMaterial.thresholdPublicKey.value.toHex(),
                    secretShare = "cc".repeat(32),
                )
            )

            // A ceremony's signer ids come from its host keys, sorted -- there is
            // no stored signer id to seed. Without these the parent holds a key it
            // cannot find itself in, and `proposeSigningBatch` refuses before it
            // writes anything.
            (members + user).forEachIndexed { index, member ->
                db.dkgSessionDao().upsert(
                    DkgParticipantMessage(
                        sessionId = "parent-ceremony",
                        participantPublicKey = member,
                        kind = DkgRitualEvents.HOST_KEY,
                        payload = "0${index + 1}".repeat(33)
                    )
                )
            }
        }

        return assertNotNull(db.chatRoomDao().findChatRoomById(parentRoomId))
    }

    private suspend fun seedProfile(publicKey: String) {
        if (db.profileDao().getProfileByPublicKey(publicKey) != null) return

        val nostrEventId = publicKey.take(63) + "f"
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = publicKey,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(
            Profile(publicKey = publicKey, userName = "member", nostrEventId = nostrEventId)
        )
    }

    private suspend fun seedRoom(id: String) {
        seedProfile(user)
        db.chatRoomDao().upsert(
            ChatRoom(
                id = id,
                userPublicKey = user,
                subject = null,
                description = null,
                mlsGroupState = null,
            )
        )
    }

    /** Files a certificate in the parent's room, the way a session would. */
    private suspend fun record(event: Event) =
        db.groupSignedEventDao().upsert(
            GroupSignedEvent.fromEvent(
                event = event,
                chatRoomId = parentRoomId,
                derivationPath = SharedKeyDerivation.formatPath(path),
                frostSigningSessionId = null,
            )
        )

    private fun certificate(
        subgroupMaterial: KeyMaterial = childMaterial,
        name: String = "Translation team",
        adminPublicKeys: List<String> = listOf(user, alice, bob),
        tags: Array<Array<String>>? = null,
        createdAt: Long = 1_700_000_000,
        author: KeyMaterial = parentMaterial,
        signer: KeyMaterial = author,
        signature: String? = null
    ): Event {
        val subgroupChatRoomId = roomOf(subgroupMaterial)
        val eventTags = tags ?: SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentRoomId,
            thresholdPublicKey = subgroupMaterial.thresholdPublicKey.value.toHex(),
            adminPublicKeys = adminPublicKeys,
            name = name,
            path = path
        )

        val pubKey = SharedKeyDerivation
            .derive(author.thresholdPublicKey.value.toHex(), path)
            .hex

        val id = EventHasher.hashId(
            pubKey = pubKey,
            createdAt = createdAt,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = eventTags,
            content = subgroupChatRoomId
        )

        return Event(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = eventTags,
            content = subgroupChatRoomId,
            sig = signature ?: groupSignature(signer, id)
        )
    }

    private fun groupSignature(material: KeyMaterial, eventId: String): String {
        val cache = SharedKeyDerivation
            .derive(material.thresholdPublicKey.value.toHex(), path)
            .cache
        val message = ByteVector(eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = material.secretShares[signerId],
                publicShare = material.publicShares[signerId],
                tweakedThresholdPublicKey = cache.tweakedPublicKey,
                message = message,
                extraInput = null
            )
        }

        val signingSession = Session.create(
            aggregatedNonce = IndividualNonce.aggregate(nonces.map { it.second }).right!!,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { material.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = cache,
            message = message
        )

        val partials = signerIds.mapIndexed { position, signerId ->
            signingSession.sign(
                nonces[position].first,
                material.secretShares[signerId],
                signerId.toUInt()
            ).right!!
        }

        return signingSession.aggregateSigs(partials).right!!.toByteArray().toHex()
    }
}
