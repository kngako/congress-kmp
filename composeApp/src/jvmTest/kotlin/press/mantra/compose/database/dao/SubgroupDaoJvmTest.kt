package press.mantra.compose.database.dao

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupKeyStateParent
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.DkgRitualStage

/**
 * What schema 21 has to survive, and the two things it must refuse to do.
 *
 * A room's parentage is a table now rather than two nullable columns, because a
 * room can be the child of more than one group -- founded with one and adopted by
 * another later. Four properties of that are invisible to the compiler and each
 * one is a way a lineage could break quietly.
 *
 * A parent pointer **may name a room this device does not have**. A member of a
 * subgroup who was never in its parent holds the id and nothing else, so a
 * dangling value is the normal state rather than an error -- and if the parent
 * side were ever given a foreign key, that member's rows would stop being
 * writable at all.
 *
 * Deleting a parent **must not touch its children**. Every other reference to a
 * room in this schema cascades. A self-referential cascade would mean a user
 * tidying away a group they had left silently destroying a group they are still
 * in, together with its messages, participants, key state and signed work.
 *
 * A state's lineage **is replaced wholesale, never merged**. A key state is
 * replaceable and the newest wins, so the set of parents it names is the set.
 * That is what makes a disavowal expressible at all: a room drops a parent by
 * re-announcing without it, and merged rows would leave the dropped parent
 * surviving the statement that dropped it.
 *
 * And a lineage **does not outlive the state that claimed it**, which is the one
 * cascade here that is wanted.
 */
class SubgroupDaoJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val user = KeyPair().pubKey.toHexKey()

    private val parentRoom = "11".repeat(32)
    private val childRoom = "22".repeat(32)

    /** A parent this device has never held a room for. */
    private val absentParent = "33".repeat(32)

    private fun certificate(by: String) =
        """{"id":"aa","pubkey":"$by","created_at":1700000000,"kind":30329,""" +
            """"tags":[],"content":"cc","sig":"dd"}"""

    @Test
    fun `a key state keeps every parent and the certificate behind each`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(parentRoom), parentage(absentParent))
        )

        val stored = db.groupKeyStateDao().getLocalByChatRoomId(childRoom)

        assertEquals(
            listOf(parentRoom, absentParent).sorted(),
            stored?.parents?.map { it.parentChatRoomId }?.sorted()
        )
        assertEquals(
            certificate(parentRoom),
            stored?.parents?.single { it.parentChatRoomId == parentRoom }?.birthCertificateJson
        )
    }

    @Test
    fun `a group that is nobody's child carries no parent rows`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(keyState(chatRoomId = parentRoom))

        val stored = db.groupKeyStateDao().getLocalByChatRoomId(parentRoom)

        // Every group made before subgroups existed reads back this way, and so
        // does every top-level group made after. No rows is not missing data, and
        // it is one way of saying "nobody's child" rather than two.
        assertEquals(emptyList(), stored?.parents)
        assertNull(stored?.lineage())
    }

    @Test
    fun `a parentage may name a parent this device holds no room for`() = runBlocking {
        seedRooms()

        // The position a subgroup member who was never in the parent is in: they
        // hold the id off a certificate and nothing else. Were the parent side a
        // foreign key their lineage could not be written at all.
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(absentParent))
        )

        assertEquals(
            listOf(absentParent),
            db.groupKeyStateDao().getLocalByChatRoomId(childRoom)?.parents?.map { it.parentChatRoomId }
        )
        assertNull(db.chatRoomDao().findChatRoomById(absentParent))
    }

    @Test
    fun `deleting a parent room leaves its subgroup standing`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(parentRoom))
        )

        db.chatRoomDao().delete(room(parentRoom))

        // The child survives with its lineage intact and pointing at a room that
        // is gone, which is exactly the dangling-reference case above. A cascade
        // here would have taken the room, its key state and everything either of
        // them owns.
        assertNull(db.chatRoomDao().findChatRoomById(parentRoom))
        assertEquals(
            listOf(parentRoom),
            db.groupKeyStateDao().getLocalByChatRoomId(childRoom)?.parents?.map { it.parentChatRoomId }
        )
    }

    @Test
    fun `deleting the child takes its lineage with it`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(parentRoom))
        )

        // The one cascade that is wanted: a lineage must not outlive the state
        // that claimed it, or a room recreated at the same derived id would
        // inherit a parentage nobody signed for it.
        db.chatRoomDao().delete(room(childRoom))

        assertNull(db.groupKeyStateDao().getByChatRoomId(childRoom))
        assertEquals(emptyList(), db.groupKeyStateDao().getParentsByChatRoomId(childRoom))
    }

    @Test
    fun `a parent lists the states of the children it certified, newest first`() = runBlocking {
        seedRooms()

        val second = "44".repeat(32)
        db.chatRoomDao().upsert(room(second))

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(1_000)),
            listOf(parentage(parentRoom))
        )
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = second, announcedAt = Instant.fromEpochSeconds(2_000)),
            listOf(parentage(parentRoom, child = second))
        )
        // Another group's child, to prove the filter is on the parent rather
        // than on having a parent at all.
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = parentRoom),
            listOf(parentage(absentParent, child = parentRoom))
        )

        assertEquals(
            listOf(second, childRoom),
            db.groupKeyStateDao().getByParentChatRoomId(parentRoom).map { it.keyState.chatRoomId }
        )
        assertEquals(
            listOf(second, childRoom),
            db.groupKeyStateDao().observeByParentChatRoomId(parentRoom).first()
                .map { it.keyState.chatRoomId }
        )
    }

    @Test
    fun `a child with two parents appears in both their lists`() = runBlocking {
        seedRooms()

        db.chatRoomDao().upsert(room(absentParent))
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(parentRoom), parentage(absentParent))
        )

        // The row that makes it this parent's child says nothing about who else
        // certified it, which is the whole point of the join.
        listOf(parentRoom, absentParent).forEach { parent ->
            assertEquals(
                listOf(childRoom),
                db.groupKeyStateDao().getByParentChatRoomId(parent).map { it.keyState.chatRoomId },
                "$parent should list the child it certified"
            )
        }
    }

    @Test
    fun `a newer state replaces the lineage rather than adding to it`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(1_000)),
            listOf(parentage(parentRoom), parentage(absentParent))
        )

        // A disavowal: the child re-announces without one of them. Were these
        // rows merged, the dropped parent would survive the statement dropping
        // it -- and a parent nobody can drop is a parent for life.
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(2_000)),
            listOf(parentage(parentRoom))
        )

        assertEquals(
            listOf(parentRoom),
            db.groupKeyStateDao().getLocalByChatRoomId(childRoom)?.parents?.map { it.parentChatRoomId }
        )
        assertTrue(db.groupKeyStateDao().getByParentChatRoomId(absentParent).isEmpty())
    }

    @Test
    fun `a re-announcement to nobody's child clears the lineage`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(1_000)),
            listOf(parentage(parentRoom))
        )
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(2_000))
        )

        assertEquals(emptyList(), db.groupKeyStateDao().getParentsByChatRoomId(childRoom))
    }

    @Test
    fun `an older state changes neither the state nor the lineage on file`() = runBlocking {
        seedRooms()

        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(2_000)),
            listOf(parentage(parentRoom))
        )

        // A session replayed after a crash must not walk the room back to a
        // state it has already moved past -- and must not walk its lineage back
        // either, which is new: the parents are part of the statement now.
        val kept = db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom, announcedAt = Instant.fromEpochSeconds(1_000)),
            listOf(parentage(absentParent))
        )

        assertEquals(Instant.fromEpochSeconds(2_000), kept.keyState.announcedAt)
        assertEquals(listOf(parentRoom), kept.parents.map { it.parentChatRoomId })
        assertEquals(
            listOf(parentRoom),
            db.groupKeyStateDao().getLocalByChatRoomId(childRoom)?.parents?.map { it.parentChatRoomId }
        )
    }

    @Test
    fun `one parent gets one row, whatever a caller tries`() = runBlocking {
        seedRooms()

        // The primary key is the rule "one certificate per child per parent".
        // `SubgroupLineage.of` refuses a repeat long before here; this is the
        // store saying so too, so the rule does not rest on a reader remembering.
        db.groupKeyStateDao().replace(
            keyState(chatRoomId = childRoom),
            listOf(parentage(parentRoom), parentage(parentRoom))
        )

        assertEquals(1, db.groupKeyStateDao().getParentsByChatRoomId(childRoom).size)
    }

    @Test
    fun `a ceremony remembers which group it was opened to make a subgroup of`() = runBlocking {
        seedRooms()

        db.dkgSessionDao().upsert(session("s1", parentChatRoomId = parentRoom))
        db.dkgSessionDao().upsert(session("s2"))

        // Unverified, and read for nothing but a screen's title and a way back
        // into an unfinished flow -- but it has to survive a round trip or there
        // is no way back at all.
        assertEquals(
            listOf("s1"),
            db.dkgSessionDao().getByParentChatRoomId(parentRoom, user).map { it.id }
        )
        assertNull(db.dkgSessionDao().getSessionById("s2", user)?.parentChatRoomId)
    }

    private suspend fun seedRooms() {
        val nostrEventId = "c".repeat(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = user,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(
            Profile(publicKey = user, userName = "user", nostrEventId = nostrEventId)
        )
        listOf(parentRoom, childRoom).forEach { db.chatRoomDao().upsert(room(it)) }
    }

    private fun room(id: String) = ChatRoom(
        id = id,
        subject = null,
        description = null
    )

    private fun parentage(parentChatRoomId: String, child: String = childRoom) =
        GroupKeyStateParent(
            chatRoomId = child,
            parentChatRoomId = parentChatRoomId,
            birthCertificateJson = certificate(parentChatRoomId),
        )

    private fun keyState(
        chatRoomId: String,
        announcedAt: Instant = Instant.fromEpochSeconds(1_700_000_000)
    ) = GroupKeyState(
        chatRoomId = chatRoomId,
        dkgSessionId = "ceremony-1",
        thresholdPublicKey = "02" + "ab".repeat(32),
        derivationPath = "m/9420/0/0",
        announcedBy = user,
        announcedAt = announcedAt,
    )

    private fun session(
        id: String,
        chatRoomId: String = parentRoom,
        parentChatRoomId: String? = null
    ) = DkgSession(
        id = id,
        chatRoomId = chatRoomId,
        coordinatorPublicKey = user,
        userPublicKey = user,
        threshold = 2,
        participantCount = 3,
        stage = DkgRitualStage.COLLECTING_HOST_KEYS,
        hostPublicKey = user,
        round1Random = "aa".repeat(32),
        round2AuxRandom = "bb".repeat(32),
        parentChatRoomId = parentChatRoomId,
    )
}
