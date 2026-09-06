package press.mantra.compose.database.dao

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.exceptions.MarmotMissingChatGroupException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The membership guards on [MarmotOutboundDao].
 *
 * Both entry points that change a group's membership begin by restoring the MLS state off the
 * ChatRoom row, and a room restored from an inbound gift wrap has none -- there is nothing to
 * add a member to. The comment on `inviteMemberToChatRoom` says the point of the throw is to
 * "say so instead of silently doing nothing and letting the caller report success", which is a
 * claim about behaviour and therefore testable: a guard that returned quietly would still
 * compile, still look like it worked, and leave a room whose members believe someone was
 * invited.
 *
 * These cover the paths that need no MLS material. Everything past the guard -- the commit, the
 * Welcome, the epoch advance and its persistence -- needs a real peer key package to exercise,
 * which means an MLS fixture this test file deliberately does not build. Those paths are worth
 * covering and are not covered here.
 */
class MarmotOutboundDaoJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val user = KeyPair().pubKey.toHexKey()
    private val peer = KeyPair().pubKey.toHexKey()
    private val roomId = "b".repeat(64)

    /**
     * A room with `mlsGroupState = null` -- exactly the shape a room restored from an inbound
     * gift wrap has, which is the case the guard exists for.
     */
    private suspend fun seedStatelessRoom(): LocalChatRoom {
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
        db.profileDao().upsert(Profile(publicKey = user, userName = "user", nostrEventId = nostrEventId))
        val chatRoom = ChatRoom(
            id = roomId,
            userPublicKey = user,
            subject = "a restored room",
            description = null,
            mlsGroupState = null,
        )
        db.chatRoomDao().upsert(chatRoom)
        return LocalChatRoom(chatRoom = chatRoom)
    }

    /**
     * Never decoded: the guard throws before any of these tests reach the MLS layer, so the
     * bytes only have to exist. A test that got past the guard would need a real key package.
     */
    private fun keyPackage() = MarmotKeyPackage(
        id = "d".repeat(64),
        publicKey = peer,
        tlsEncodedMarmotKeyPackage = ByteArray(0),
    )

    @Test
    fun `inviting into a room with no mls state is refused rather than ignored`() = runBlocking<Unit> {
        val localChatRoom = seedStatelessRoom()

        assertFailsWith<MarmotMissingChatGroupException> {
            db.marmotOutboundDao().inviteMemberToChatRoom(
                localChatRoom = localChatRoom,
                peerPublicKey = peer,
                peerKeyPackage = keyPackage(),
            )
        }
    }

    /**
     * The guard has to come before the Participant write, not after. `sealGiftWrapPayload`
     * walks a room's participants to decide who to wrap a Welcome for, so a participant row
     * left behind by a failed invite would make the room look like it has a member that no MLS
     * group knows about.
     */
    @Test
    fun `a refused invite leaves no participant behind`() = runBlocking {
        val localChatRoom = seedStatelessRoom()

        runCatching {
            db.marmotOutboundDao().inviteMemberToChatRoom(
                localChatRoom = localChatRoom,
                peerPublicKey = peer,
                peerKeyPackage = keyPackage(),
            )
        }

        val participants = db.participantDao().findParticipantsByChatRoomId(roomId)
        assertTrue(
            participants.none { it.participantPublicKey == peer },
            "the invitee was persisted despite the invite being refused",
        )
    }

    @Test
    fun `adding members to a room with no mls state is refused`() = runBlocking<Unit> {
        val localChatRoom = seedStatelessRoom()

        assertFailsWith<MarmotMissingChatGroupException> {
            db.marmotOutboundDao().addMembersToChatRoom(
                localChatRoom = localChatRoom,
                peers = listOf(peer to keyPackage()),
            )
        }
    }

    /**
     * The empty-batch guard returns before the MLS state is even looked at, so it must not
     * throw on the same stateless room the two tests above reject. Adding nobody is not a
     * failure to add somebody.
     */
    @Test
    fun `adding no members is not an error even without mls state`() = runBlocking {
        val localChatRoom = seedStatelessRoom()

        val failed = db.marmotOutboundDao().addMembersToChatRoom(
            localChatRoom = localChatRoom,
            peers = emptyList(),
        )

        assertEquals(emptyList(), failed)
    }
}
