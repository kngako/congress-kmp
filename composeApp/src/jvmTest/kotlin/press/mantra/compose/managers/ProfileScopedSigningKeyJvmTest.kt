package press.mantra.compose.managers

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.phoenix.managers.nostrPublicKeyHex
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.UserPrefs
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import okio.Path.Companion.toPath
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.repository.DatabaseFrostSigningRepository
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.identity.Identity
import press.mantra.compose.identity.IdentityKind
import press.mantra.compose.identity.toWalletId

/**
 * One device, two profiles, one database -- and only one of them holds the share.
 *
 * Each profile has its own groups and its own ceremonies, and every one of those
 * rows lands in this one table -- `ChatRoom` is keyed by the group id alone, so
 * even the rooms are shared, and the only thing separating one profile's ceremony
 * from the other's is `DkgSession.userPublicKey`. Every one of `completedKey`'s
 * three answers used to ignore that column, so the question "can this group sign"
 * was answered about the *device* and read by the UI as being about the user in
 * front of it.
 *
 * What that costs is not a secret: both shares are already one person's, on one
 * disk, and the share handed over is not one the asking profile can make a valid
 * signature with -- `signerIdOf` throws, because that profile is no participant of
 * the ceremony it was given. What it costs is the separation between two personas,
 * which is the whole of what a profile is, plus a button that fails after having
 * been offered.
 *
 * Three tests, one per answer, because each resolves a key by a different route
 * and each had to be scoped separately. The fourth is the seam the UI reads.
 */
class ProfileScopedSigningKeyJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val aliceKey = PrivateKey(ByteVector32("0a".repeat(32)))
    private val bobKey = PrivateKey(ByteVector32("0b".repeat(32)))

    /** The two profiles signed in on this device. */
    private val alice: HexKey = aliceKey.nostrPublicKeyHex()
    private val bob: HexKey = bobKey.nostrPublicKeyHex()

    /** The room the group's ceremony ran in: a NIP-17 room, derived from nobody. */
    private val ceremonyRoomId = "11".repeat(32)

    /** A group only Bob is in, so neither profile is tested against an empty table. */
    private val bobsRoomId = "22".repeat(32)

    /**
     * Alice's group's key. Dealt rather than run: what is under test is which row a
     * lookup returns, not whether ChillDKG agrees on a key.
     */
    private val aliceMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("a11ce00000000000000000000000000000000000000000000000000000000001")
        ),
        nParticipants = 3,
        threshold = 2
    )

    private val bobMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("b0b0000000000000000000000000000000000000000000000000000000000002")
        ),
        nParticipants = 3,
        threshold = 2
    )

    private val aliceThresholdKey: HexKey get() = aliceMaterial.thresholdPublicKey.value.toHex()
    private val bobThresholdKey: HexKey get() = bobMaterial.thresholdPublicKey.value.toHex()

    /** Alice's group's admin room: its id *is* her group's key at the app's path. */
    private val aliceAdminRoomId: String get() = SharedKeyDerivation.marmotGroupId(aliceThresholdKey)

    /**
     * Alice's ceremony, as her profile holds it, and Bob's of his own group.
     *
     * Both complete and both holding a share, which is what makes them findable at
     * all. Alice's is filed under the room it ran in and claims no parent, so it is
     * her group's own key by every route `completedKey` has.
     */
    private suspend fun seedTwoProfiles() {
        seedProfile(alice)
        seedProfile(bob)

        // One row per room, as the schema has it -- the second profile does not get
        // a room of its own, which is exactly why the ceremony has to carry the
        // distinction.
        seedRoom(ceremonyRoomId, userPublicKey = alice)
        seedRoom(bobsRoomId, userPublicKey = bob)

        seedCeremony(
            id = "alices-ceremony",
            chatRoomId = ceremonyRoomId,
            userPublicKey = alice,
            thresholdPublicKey = aliceThresholdKey,
        )
        seedCeremony(
            id = "bobs-ceremony",
            chatRoomId = bobsRoomId,
            userPublicKey = bob,
            thresholdPublicKey = bobThresholdKey,
        )
    }

    /**
     * The scan over every ceremony a share is held for, which is how a real admin
     * room resolves its key: rederive each one's room id and match.
     */
    @Test
    fun `the rederivation scan reaches only the asking profiles ceremony`(): Unit = runBlocking {
        seedTwoProfiles()

        assertEquals(
            "alices-ceremony",
            FrostSigningManager.completedKey(db, aliceAdminRoomId, alice)?.id,
            "the profile that ran the ceremony signs with it",
        )
        assertTrue(FrostSigningManager.canSign(db, aliceAdminRoomId, alice))

        assertNull(
            FrostSigningManager.completedKey(db, aliceAdminRoomId, bob),
            "the other profile on this device holds no share of Alice's group",
        )
        assertFalse(
            FrostSigningManager.canSign(db, aliceAdminRoomId, bob),
            "and must not be offered an action that would throw at the button",
        )

        // Not a database that answers nothing: Bob resolves his own group, in the
        // same scan, from the same table.
        assertEquals(
            "bobs-ceremony",
            FrostSigningManager.completedKey(
                db,
                SharedKeyDerivation.marmotGroupId(bobThresholdKey),
                bob
            )?.id,
        )
    }

    /**
     * The last fallback: a ceremony held in this very room. It is what every member
     * welcomed after the group's own ceremony reaches, holding no key state.
     *
     * Reached here because the room asked about is the NIP-17 room the ceremony ran
     * in, which is an aggregate of its members' keys and derived from no threshold
     * key at all -- so the scan above cannot match it.
     */
    @Test
    fun `a rooms own ceremony belongs to one profile`(): Unit = runBlocking {
        seedTwoProfiles()

        assertEquals(
            "alices-ceremony",
            FrostSigningManager.completedKey(db, ceremonyRoomId, alice)?.id,
        )
        assertNull(
            FrostSigningManager.completedKey(db, ceremonyRoomId, bob),
            "a room's own ceremony is the asking profile's own or it is nothing",
        )
    }

    /**
     * The lookup in front of both fallbacks: the room's `GroupKeyState`, naming the
     * ceremony behind it.
     *
     * Filtered rather than scoped, because it cannot be scoped -- `DkgSession`'s
     * primary key is the ceremony id the coordinator minted, the same string on
     * every participant's device, so there is one row per ceremony and no second
     * one to find. A profile that does not own that row resolves nothing, which is
     * the honest answer: it has no share of it.
     *
     * The room here is derived at a path other than the app's default, so the
     * rederivation scan cannot reach the ceremony and the key state is the only
     * thing that can answer -- which is the case the state was added for.
     */
    @Test
    fun `a key state naming another profiles ceremony resolves nothing`(): Unit = runBlocking {
        seedTwoProfiles()

        val path = listOf(9420L, 0L, 1L)
        val roomId = SharedKeyDerivation.marmotGroupId(aliceThresholdKey, path)
        seedRoom(roomId, userPublicKey = alice)

        db.groupKeyStateDao().upsert(
            GroupKeyState(
                chatRoomId = roomId,
                dkgSessionId = "alices-ceremony",
                thresholdPublicKey = aliceThresholdKey,
                derivationPath = SharedKeyDerivation.formatPath(path),
                announcedBy = alice,
                announcedAt = Instant.fromEpochSeconds(1_000),
            )
        )

        assertEquals(
            "alices-ceremony",
            FrostSigningManager.completedKey(db, roomId, alice)?.id,
            "the key state is the only route to a room derived off the app's path",
        )
        assertNull(
            FrostSigningManager.completedKey(db, roomId, bob),
            "a state Bob's profile holds no share for is no key of his",
        )
    }

    /**
     * The seam the UI reads. Every screen that offers a signing action asks the
     * repository, which stamps the question with the profile that is open rather
     * than taking one from the call site -- a screen asking "can this group sign"
     * means itself and cannot be made to mean anyone else.
     */
    @Test
    fun `the repository answers for the profile that is open`(): Unit = runBlocking {
        seedTwoProfiles()

        val active = MutableStateFlow<Identity?>(null)
        val repository = DatabaseFrostSigningRepository(db, scope, activeIdentity = active)

        assertFalse(
            repository.canSign(aliceAdminRoomId),
            "nobody open signs nothing, whatever the device holds",
        )

        active.value = identity(aliceKey)
        assertTrue(repository.canSign(aliceAdminRoomId))

        active.value = identity(bobKey)
        assertFalse(
            repository.canSign(aliceAdminRoomId),
            "switching profile switches the answer, without the room changing",
        )
        assertTrue(
            repository.canSign(SharedKeyDerivation.marmotGroupId(bobThresholdKey)),
            "and Bob can still sign for his own group",
        )
    }

    private suspend fun seedCeremony(
        id: String,
        chatRoomId: String,
        userPublicKey: HexKey,
        thresholdPublicKey: HexKey,
    ) = db.dkgSessionDao().upsert(
        DkgSession(
            id = id,
            chatRoomId = chatRoomId,
            coordinatorPublicKey = userPublicKey,
            userPublicKey = userPublicKey,
            threshold = 2,
            participantCount = 3,
            stage = DkgRitualStage.COMPLETE,
            hostPublicKey = userPublicKey,
            round1Random = "aa".repeat(32),
            round2AuxRandom = "bb".repeat(32),
            thresholdPublicKey = thresholdPublicKey,
            secretShare = "cc".repeat(32),
        )
    )

    private suspend fun seedRoom(id: String, userPublicKey: HexKey) =
        db.chatRoomDao().upsert(
            ChatRoom(
                id = id,
                userPublicKey = userPublicKey,
                subject = "Ekklesia",
                description = null,
                mlsGroupState = null,
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
