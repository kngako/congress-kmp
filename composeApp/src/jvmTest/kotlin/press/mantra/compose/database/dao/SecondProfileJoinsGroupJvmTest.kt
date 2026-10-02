package press.mantra.compose.database.dao

import androidx.room3.Room
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatRoomMembership
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.Relays
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Two profiles on one device, both members of one group.
 *
 * This file used to assert the opposite. `679d5249` refused a Welcome for a group another
 * profile here already held, because `ChatRoom` was keyed by the group id alone and the
 * joiner's MLS state would have gone over the holder's -- breaking both members, in the
 * shape of "the other device never got it". docs/curated-to-mantra-profiles.md called that
 * the cheap fix and keying the room per profile the real one.
 *
 * The real one landed: v24 moved the MLS state onto `ChatRoomMembership`, and v25 took the
 * last per-viewer field off a group-wide transcript row. So the refusal is gone, and what
 * is pinned here is that a second profile joins properly -- its own membership, its own
 * ratchet state, the holder's untouched beside it.
 *
 * Removing the refusal was not enough on its own, which is the thing this test is really
 * for. The branch keyed on the *room*, so a Welcome that found one fell through and did
 * nothing: the accidental refusal that commit was written to make legible in the first
 * place. It asks about the membership now.
 *
 * The setup is real: an MLS group on the inviter's database, a key package whose private
 * keys the invitee's database holds, and a Welcome sealed the way
 * `DatabaseChatRepository.sealGiftWrapPayload` seals one, delivered through
 * `storeNostrEvent` under the invitee's key.
 */
class SecondProfileJoinsGroupJvmTest {

    /** The inviter's device. */
    private val inviterDb: MantraDatabase = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

    /** The device that holds two profiles: `holder`, already in the group, and `invitee`. */
    private val deviceDb: MantraDatabase = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

    @AfterTest
    fun closeDbs() {
        inviterDb.close()
        deviceDb.close()
    }

    private val relay = Relays.DefaultDMRelayList.first().url
    private val inviter = KeyPair()
    private val invitee = KeyPair()
    private val holder = KeyPair()
    private val roomId = "b".repeat(64)
    private val keyPackageEventId = "d".repeat(64)

    private suspend fun MantraDatabase.seedProfile(publicKey: HexKey, name: String, nostrEventId: String) {
        nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = publicKey,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        profileDao().upsert(Profile(publicKey = publicKey, userName = name, nostrEventId = nostrEventId))
    }

    /** A room with real MLS state on the inviter's device, the inviter its only member. */
    private suspend fun seedInviterRoom(): LocalChatRoom {
        val inviterHex = inviter.pubKey.toHexKey()
        inviterDb.seedProfile(inviterHex, "inviter", "1".repeat(64))
        inviterDb.seedProfile(invitee.pubKey.toHexKey(), "invitee", "2".repeat(64))
        val mlsGroup = MlsGroup.create(
            identity = inviterHex.hexToByteArray(),
            initialExtensions = listOf(
                MarmotGroupData.bootstrap(
                    nostrGroupId = roomId,
                    creatorPubKey = inviterHex,
                    outboxRelays = listOf(relay),
                ).toExtension()
            ),
        )
        val chatRoom = ChatRoom(
            id = roomId,
            subject = "the collective",
            description = null
        )
        inviterDb.chatRoomDao().upsert(chatRoom)
        // The inviter's own membership, which is what holds the MLS state they invite from.
        inviterDb.chatRoomDao().upsert(
            ChatRoomMembership(
                chatRoomId = roomId,
                userPublicKey = inviterHex,
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            )
        )
        return assertNotNull(inviterDb.chatRoomDao().findChatRoomById(roomId))
    }

    /**
     * The inviter adds the invitee and the Welcome reaches the queue; this seals it for
     * the invitee the way the notary would, and hands back the wrap as a relay would.
     */
    private suspend fun welcomeWrapForInvitee(): NostrEvent {
        // The room first: MarmotKeyPackage.publicKey is a foreign key onto Profile, and
        // the room's seeding is what gives the invitee one on the inviter's device.
        val localChatRoom = seedInviterRoom()
        val pair = marmotKeyPackageBundleFor(invitee.pubKey.toHexKey(), id = keyPackageEventId)
        inviterDb.marmotKeyPackageDao().upsert(pair.keyPackage)
        deviceDb.marmotKeyPackageBundleDao().upsert(pair.bundle)

        inviterDb.marmotOutboundDao().inviteMemberToChatRoom(
            localChatRoom = localChatRoom,
            userPublicKey = inviter.pubKey.toHexKey(),
            peerPublicKey = invitee.pubKey.toHexKey(),
            peerKeyPackage = pair.keyPackage,
        )
        val payload = inviterDb.giftWrapPayloadDao()
            .getByChatRoomAndKinds(roomId, listOf(WelcomeEvent.KIND))
            .single()

        val signer = NostrSignerSync(inviter)
        val rumor = RumorAssembler.assembleRumor(
            pubKey = signer.pubKey,
            ev = EventTemplate(
                createdAt = payload.createdAt.epochSeconds,
                kind = payload.kind,
                tags = payload.tags,
                content = payload.content,
            ),
        )
        val seal = signer.signNormal<SealedRumorEvent>(
            createdAt = 1_700_000_000,
            kind = SealedRumorEvent.KIND,
            tags = emptyArray(),
            content = signer.nip44Encrypt(plaintext = rumor.toJson(), toPublicKey = invitee.pubKey.toHexKey()),
        )
        val wrap = GiftWrapEvent.create(
            event = seal,
            recipientPubKey = invitee.pubKey.toHexKey(),
            createdAt = 1_700_000_100,
        )
        return NostrEvent(
            id = wrap.id,
            pubKey = wrap.pubKey,
            kind = wrap.kind,
            tags = wrap.tags,
            content = wrap.content,
            sig = wrap.sig,
            createdAt = Instant.fromEpochSeconds(wrap.createdAt),
        )
    }

    /** The group, already on the device under [owner], with state that is recognisably not the joiner's. */
    /**
     * The room, plus the membership that makes [owner] its holder on this device.
     *
     * The membership is the point of the fixture since v23: a room is the group's and
     * says nothing about who holds it, so what a second Welcome would have overwritten
     * -- and what the refusal protects -- is this row.
     */
    private suspend fun seedDeviceRoom(owner: KeyPair): ChatRoomMembership {
        val ownerHex = owner.pubKey.toHexKey()
        deviceDb.seedProfile(ownerHex, "holder", "3".repeat(64))
        deviceDb.chatRoomDao().upsert(
            ChatRoom(
                id = roomId,
                subject = "the collective",
                description = null
            )
        )
        val membership = ChatRoomMembership(
            chatRoomId = roomId,
            userPublicKey = ownerHex,
            mlsGroupState = "a1".repeat(32),
        )
        deviceDb.chatRoomDao().upsert(membership)
        return membership
    }

    private suspend fun deliverToInvitee(wrap: NostrEvent) = deviceDb.nostrDao().storeNostrEvent(
        nostrEvent = wrap,
        relayURL = relay,
        synchronizationRelayURLs = listOf(relay),
        level = 0,
        activeKeyPair = invitee,
    )

    @Test
    fun `a second profile on the device joins a group the first already holds`() = runBlocking {
        val holders = seedDeviceRoom(holder)
        deviceDb.seedProfile(invitee.pubKey.toHexKey(), "second self", "4".repeat(64))
        // The holder is in the group with the inviter, so this device knows them by name.
        deviceDb.seedProfile(inviter.pubKey.toHexKey(), "inviter", "5".repeat(64))

        deliverToInvitee(welcomeWrapForInvitee())

        val room = assertNotNull(
            deviceDb.chatRoomDao().findChatRoomById(roomId),
            "the group is still here",
        )

        // Two memberships of one room, which is the shape the whole line of work was for.
        val memberships = deviceDb.chatRoomDao().membershipsOf(roomId).associateBy { it.userPublicKey }
        assertEquals(
            setOf(holder.pubKey.toHexKey(), invitee.pubKey.toHexKey()),
            memberships.keys,
            "the second profile did not join",
        )

        val joined = assertNotNull(memberships[invitee.pubKey.toHexKey()])
        assertNotNull(joined.mlsGroupState, "the joiner's own MLS state was not filed")
        assertNotNull(joined.joinedGroupAt, "and nor was when their epoch began")

        // The point of v24: the holder's ratchet state is exactly where it was.
        assertEquals(
            holders.mlsGroupState,
            memberships.getValue(holder.pubKey.toHexKey()).mlsGroupState,
            "the holder's MLS state was overwritten by the joiner's",
        )
        assertNotEquals(
            holders.mlsGroupState,
            joined.mlsGroupState,
            "two members of a tree do not hold the same state",
        )

        // And the room reads back with both, which is what every per-profile accessor
        // on LocalChatRoom resolves against.
        assertNotNull(room.membershipOf(holder.pubKey.toHexKey()))
        assertNotNull(room.membershipOf(invitee.pubKey.toHexKey()))

        // No refusal line, and the key package is spent like any other join's.
        assertEquals(
            emptyList(),
            deviceDb.chatMessageDao().getChatMessagesByChatRoomId(roomId).map { it.chatMessage }
                .filter { it.messageType == ChatMessage.TYPE_WELCOME_REFUSED_OTHER_PROFILE },
            "a join must not leave a refusal behind",
        )
        assertTrue(
            assertNotNull(
                deviceDb.marmotKeyPackageBundleDao().getMarmotKeyPackageBundleById(keyPackageEventId)
            ).consumed,
            "a join consumes the key package",
        )
    }

    /** The one case left in that branch: the same profile, welcomed twice, is a no-op. */
    @Test
    fun `a welcome for a group the same profile already holds writes nothing`() = runBlocking {
        seedDeviceRoom(invitee)

        deliverToInvitee(welcomeWrapForInvitee())

        assertEquals(
            emptyList(),
            deviceDb.chatMessageDao().getChatMessagesByChatRoomId(roomId).map { it.chatMessage.messageType },
            "a redelivered welcome for a group we are already in writes nothing",
        )
    }

    /** And with nothing in the way, the Welcome is a join, which is what tells the fixture is real. */
    @Test
    fun `the same welcome joins when no profile on the device holds the group`() = runBlocking {
        deviceDb.seedProfile(invitee.pubKey.toHexKey(), "second self", "4".repeat(64))

        deliverToInvitee(welcomeWrapForInvitee())

        val room = assertNotNull(
            deviceDb.chatRoomDao().findChatRoomById(roomId),
            "the invitee did not join",
        )
        val membership = assertNotNull(
            room.membershipOf(invitee.pubKey.toHexKey()),
            "the joiner got no membership",
        )
        assertNotNull(membership.mlsGroupState, "the joiner's MLS state was not filed")
        assertTrue(
            assertNotNull(deviceDb.marmotKeyPackageBundleDao().getMarmotKeyPackageBundleById(keyPackageEventId)).consumed,
            "a join consumes the key package",
        )
    }
}
