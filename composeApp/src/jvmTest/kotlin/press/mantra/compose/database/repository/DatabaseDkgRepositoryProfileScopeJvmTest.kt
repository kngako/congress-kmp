package press.mantra.compose.database.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.managers.nostrPublicKeyHex
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.UserPrefs
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import okio.Path.Companion.toPath
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.identity.Identity
import press.mantra.compose.identity.IdentityKind
import press.mantra.compose.identity.toWalletId
import kotlin.time.Instant

/**
 * The seam between the ceremony screens and the ritual rows.
 *
 * `DkgSession` is keyed by the ceremony *and* the profile, so none of these reads
 * names a row without one. The profile is the repository's to supply rather than
 * each screen's: a screen asking about "the ceremony" means the one it is showing,
 * for the person in front of it, and cannot be made to mean anyone else. That is the
 * same arrangement `DatabaseNostrRepository` has for the fetch queues and the other
 * two repositories have for `canSign`.
 *
 * Which leaves one case with no answer at all -- nothing open -- and these pin what
 * it does. A flow emits its empty value rather than the device's rows, and `approve`
 * publishes nothing. Not a case the app reaches with a screen on top of it, but the
 * flows are collected from view models that outlive an identity switch, and
 * "whichever profile the database ordered first" is the one answer that must not
 * come back.
 */
class DatabaseDkgRepositoryProfileScopeJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val aliceKey = PrivateKey(ByteVector32("0a".repeat(32)))
    private val bobKey = PrivateKey(ByteVector32("0b".repeat(32)))
    private val alice: HexKey = aliceKey.nostrPublicKeyHex()
    private val bob: HexKey = bobKey.nostrPublicKeyHex()

    private val roomId = "11".repeat(32)

    /** Nobody open, until a test says otherwise. */
    private val active = MutableStateFlow<Identity?>(null)

    private val repository by lazy { DatabaseDkgRepository(db, scope, activeIdentity = active) }

    /**
     * One room, and a ceremony in it for each profile -- the shape that makes an
     * unscoped read ambiguous rather than merely wrong.
     */
    private suspend fun seed() {
        listOf(alice, bob).forEach { seedProfile(it) }
        db.chatRoomDao().upsert(
            ChatRoom(
                id = roomId,
                userPublicKey = alice,
                subject = "Ekklesia",
                description = null,
                mlsGroupState = null,
            )
        )
        seedCeremony("alices", alice)
        seedCeremony("bobs", bob)
        // One message in each profile's view, from the other member.
        db.dkgSessionDao().upsert(
            DkgParticipantMessage(
                sessionId = "alices",
                userPublicKey = alice,
                participantPublicKey = bob,
                kind = 30450,
                payload = "01".repeat(33),
            )
        )
        db.dkgSessionDao().upsert(
            DkgParticipantMessage(
                sessionId = "bobs",
                userPublicKey = bob,
                participantPublicKey = alice,
                kind = 30450,
                payload = "02".repeat(33),
            )
        )
    }

    @Test
    fun `nothing open reads no rituals`() = runBlocking {
        seed()

        assertNull(
            repository.observeSessionById("alices").first(),
            "a ceremony id alone names no row, so there is nothing to emit",
        )
        assertEquals(emptyList(), repository.observeSessionsForChatRoom(roomId).first())
        assertNull(repository.observeLatestSessionForChatRoom(roomId).first())
        assertEquals(emptyList(), repository.observeMessages("alices").first())
        assertNull(repository.getLatestSessionForChatRoom(roomId))
    }

    @Test
    fun `the profile that is open reads its own, and only its own`() = runBlocking {
        seed()

        active.value = identity(aliceKey)

        assertEquals("alices", repository.observeSessionById("alices").first()?.id)
        assertNull(
            repository.observeSessionById("bobs").first(),
            "the other profile's ceremony is not this one's to open",
        )
        assertEquals(listOf("alices"), repository.observeSessionsForChatRoom(roomId).first().map { it.id })
        assertEquals("alices", repository.observeLatestSessionForChatRoom(roomId).first()?.id)
        assertEquals("alices", repository.getLatestSessionForChatRoom(roomId)?.id)
        assertEquals(
            listOf(bob),
            repository.observeMessages("alices").first().map { it.participantPublicKey },
        )
    }

    /** Switching profile switches every answer, without a room or a ceremony changing. */
    @Test
    fun `switching the open profile switches the answers`() = runBlocking {
        seed()

        active.value = identity(aliceKey)
        assertEquals(listOf("alices"), repository.observeSessionsForChatRoom(roomId).first().map { it.id })

        active.value = identity(bobKey)
        assertEquals(listOf("bobs"), repository.observeSessionsForChatRoom(roomId).first().map { it.id })
        assertEquals("bobs", repository.getLatestSessionForChatRoom(roomId)?.id)
        assertEquals(
            listOf(alice),
            repository.observeMessages("bobs").first().map { it.participantPublicKey },
            "and Bob's view holds the message Bob received",
        )
    }

    /**
     * An approval is a publication: it puts this profile's host key in front of the
     * group and enrols them in its permanent signing quorum. With nobody open there
     * is no profile to publish as, and the repository stops rather than picking one.
     */
    @Test
    fun `nothing open approves nothing`() = runBlocking {
        seed()
        val room = assertNotNull(db.chatRoomDao().findChatRoomById(roomId))

        repository.approve(
            localChatRoom = room,
            sessionId = "alices",
            step = DkgApprovalStep.HOST_KEY,
            nostrPrivateKey = aliceKey.value.toByteArray(),
        )

        assertNull(
            db.dkgSessionDao().getSessionById("alices", alice)?.hostKeyApprovedAt,
            "no profile was open, so nobody agreed to anything",
        )
        assertNull(db.dkgSessionDao().getSessionById("bobs", bob)?.hostKeyApprovedAt)
        assertEquals(
            emptyList(),
            db.giftWrapPayloadDao().getByChatRoomAndKinds(roomId, listOf(30450)),
            "and nothing of anyone's went out",
        )
    }

    /**
     * The deliberate exception, pinned here as well as at the DAO: a ceremony's
     * threshold key is its published result, the same on every participant's row, and
     * the two flows that resolve the room it derives answer with no profile open.
     * Scoping them would make a screen resolve nothing for a fact that does not
     * depend on a profile -- see `DkgSessionDao.getCeremonyThresholdPublicKey`.
     */
    @Test
    fun `a ceremonys own key needs no profile open`() = runBlocking {
        seed()

        // Null because no *key state* has been signed, not because nobody is open --
        // reaching that branch at all means the ceremony's key resolved.
        assertNull(repository.observeSignedGroupKeyState("alices").first())

        assertEquals(
            "aa".repeat(32),
            db.dkgSessionDao().getCeremonyThresholdPublicKey("alices"),
            "the ceremony's key is readable with nobody open",
        )
    }

    private suspend fun seedCeremony(id: String, owner: HexKey) =
        db.dkgSessionDao().upsert(
            DkgSession(
                id = id,
                chatRoomId = roomId,
                coordinatorPublicKey = owner,
                userPublicKey = owner,
                threshold = 2,
                participantCount = 3,
                stage = DkgRitualStage.COLLECTING_HOST_KEYS,
                hostPublicKey = owner,
                round1Random = "aa".repeat(32),
                round2AuxRandom = "bb".repeat(32),
                thresholdPublicKey = "aa".repeat(32),
                createdAt = Instant.fromEpochSeconds(1_000),
            )
        )

    private suspend fun seedProfile(publicKey: HexKey) {
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
            Profile(publicKey = publicKey, userName = "profile", nostrEventId = nostrEventId)
        )
    }

    private fun prefsFile(name: String) =
        Files.createTempFile(name, ".preferences_pb").also { it.toFile().delete() }.toString().toPath()

    private fun identity(key: PrivateKey) = Identity.signing(
        id = key.publicKey().xOnly().toWalletId(),
        kind = IdentityKind.NostrSecret,
        nostrPrivateKey = key,
        userPrefs = UserPrefs(PreferenceDataStoreFactory.createWithPath { prefsFile("user") }),
        internalPrefs = InternalPrefs(PreferenceDataStoreFactory.createWithPath { prefsFile("internal") }),
        business = null,
    )
}
