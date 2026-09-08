package press.mantra.compose.managers

import androidx.room3.Room
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.extensions.toHex
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
    fun `a subgroup that is the whole group is refused`(): Unit = runBlocking {
        // The trap this exists for: the ceremony room is derived from its members,
        // so selecting everybody derives the room this group's own ceremony was
        // held in -- and `proposeRitual` would hand that ceremony back, making the
        // "child" this very group.
        val parent = parentWith(listOf(alice, bob))

        assertNotNull(refusal(parent, setOf(alice, bob)))
    }

    @Test
    fun `an admin set that already holds a ceremony is refused`(): Unit = runBlocking {
        val parent = parentWith(listOf(alice, bob, carol))

        assertNull(refusal(parent, setOf(alice, bob)))

        // One admin set, one ceremony room, forever -- the id is a pure function
        // of the members. A second subgroup with the same admins would come back
        // with the first one's key and therefore the first one's room id.
        //
        // Stood up the way `openCeremony` does, room first: the ceremony room is
        // a real NIP-17 room and `DkgSession.chatRoomId` is a foreign key onto it.
        val ceremonyRoom = assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = listOf(alice, bob),
                subject = "Translation team",
            )
        )
        assertEquals(
            SubgroupManager.ceremonyRoomIdFor(setOf(alice, bob), user),
            ceremonyRoom.chatRoom.id,
            "the derived ceremony room is the one createNip17ChatRoom lands on",
        )

        db.dkgSessionDao().upsert(
            DkgSession(
                id = "s1",
                chatRoomId = ceremonyRoom.chatRoom.id,
                coordinatorPublicKey = user,
                userPublicKey = user,
                threshold = 2,
                participantCount = 3,
                hostPublicKey = user,
                round1Random = "aa".repeat(32),
                round2AuxRandom = "bb".repeat(32),
            )
        )

        assertNotNull(refusal(parent, setOf(alice, bob)))
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

    // ---- fixtures ----------------------------------------------------------

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

    private suspend fun parentWith(members: List<String>): LocalChatRoom {
        (members + user).forEach { seedProfile(it) }

        return assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = members,
                subject = "Ekklesia",
            )
        )
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
