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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Two of a device's own profiles in one group, which docs/multiple-profiles.md made
 * possible and docs/curated-to-mantra-profiles.md's fourth decision refuses.
 *
 * `ChatRoom` is keyed by the group id alone. Before several profiles on a device the
 * second local member could not exist; after it, a user can invite their own second
 * profile into their own collective, and a Welcome processed for it would file the
 * joiner's MLS state over the holder's. The DAO already declined to overwrite -- "Chat
 * Room already exists", at warning level, and nothing else -- so the invitee never
 * joined and nobody was told. This pins the refusal and the line that now reports it.
 *
 * The setup is real: an MLS group on the inviter's database, a key package whose
 * private keys the invitee's database holds, and a Welcome sealed the way
 * `DatabaseChatRepository.sealGiftWrapPayload` seals one, delivered through
 * `storeNostrEvent` under the invitee's key.
 */
class WelcomeForOtherProfileJvmTest {

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
    fun `a welcome for a group another profile on the device holds is refused and said so`() = runBlocking {
        val before = seedDeviceRoom(holder)
        deviceDb.seedProfile(invitee.pubKey.toHexKey(), "second self", "4".repeat(64))
        // The holder is in the group with the inviter, so this device knows them by name.
        deviceDb.seedProfile(inviter.pubKey.toHexKey(), "inviter", "5".repeat(64))

        deliverToInvitee(welcomeWrapForInvitee())

        // The holder's membership is untouched, and the invitee got none: the refusal
        // leaves the device holding the group exactly as one profile's.
        val memberships = deviceDb.chatRoomDao().membershipsOf(roomId)
        assertEquals(
            listOf(before.userPublicKey),
            memberships.map { it.userPublicKey },
            "the room changed hands",
        )
        assertEquals(
            before.mlsGroupState,
            memberships.single().mlsGroupState,
            "the holder's MLS state was overwritten",
        )

        val line = deviceDb.chatMessageDao().getChatMessagesByChatRoomId(roomId).map { it.chatMessage }
            .singleOrNull { it.messageType == ChatMessage.TYPE_WELCOME_REFUSED_OTHER_PROFILE }
        assertNotNull(line, "the refusal left no line in the room")
        assertTrue(line.content.contains("second self"), "the line does not name the invitee: ${line.content}")
        assertTrue(line.content.contains("holder"), "the line does not name who holds the group: ${line.content}")
        assertTrue(line.content.contains("inviter"), "the line does not name the inviter: ${line.content}")
        assertTrue(line.messageType in ChatMessage.MEMBERSHIP_TYPES, "the line would render as a bubble")

        val bundle = assertNotNull(deviceDb.marmotKeyPackageBundleDao().getMarmotKeyPackageBundleById(keyPackageEventId))
        assertFalse(bundle.consumed, "a refused welcome must not consume the key package")
    }

    /** The branch the refusal was carved out of: the same profile, welcomed twice, is still a quiet no-op. */
    @Test
    fun `a welcome for a group the same profile already holds writes nothing`() = runBlocking {
        seedDeviceRoom(invitee)

        deliverToInvitee(welcomeWrapForInvitee())

        assertEquals(
            emptyList(),
            deviceDb.chatMessageDao().getChatMessagesByChatRoomId(roomId).map { it.chatMessage.messageType },
            "a redelivered welcome for our own room is not a refusal",
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
