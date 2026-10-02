package press.mantra.compose.ui.view.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A parent's room holds every one of its subgroups' signings, and the ceremony
 * screen has to pick out the one it is showing.
 *
 * Since a subgroup's ChillDKG moved into its parent's Marmot room, both of the
 * rungs after the ceremony run there too: the birth certificate is the parent's
 * signature, and the key state is signed over the parent's room because the
 * subgroup's own room does not exist yet. So a parent making a second subgroup
 * has two of each in one room, told apart by nothing but the ceremony they
 * belong to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DkgRitualSecondSubgroupJvmTest {
    private val parentChatRoomId = "aa".repeat(32)
    private val me = "11".repeat(32)
    private val peer = "22".repeat(32)

    // Real points, in the compressed form `SharedKeyDerivation` reads: it derives
    // a room id off the curve, and a made-up 32 bytes is not on it.
    private val firstKey = "02" + KeyPair().pubKey.toHexKey()
    private val secondKey = "02" + KeyPair().pubKey.toHexKey()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the certificate shown is this subgroup's, not the parent's newest`() = runBlocking {
        val loaded = load(
            signingSessions = listOf(
                // Newest first, the way the query returns them.
                certificateSigning("forSecond", subgroupOf(secondKey)),
                certificateSigning("forFirst", subgroupOf(firstKey)),
            ),
            until = { it.certificateSession != null }
        )

        assertEquals(
            "forFirst",
            loaded.certificateSession?.id,
            "the screen for one subgroup is following another subgroup's certificate"
        )
    }

    @Test
    fun `the key state shown is this ceremony's, not the room's newest`() = runBlocking {
        val loaded = load(
            signingSessions = listOf(
                keyStateSigning("forSecond", dkgSessionId = "secondCeremony"),
                keyStateSigning("forFirst", dkgSessionId = "firstCeremony"),
            ),
            until = { it.keyStateSession != null }
        )

        assertEquals(
            "forFirst",
            loaded.keyStateSession?.id,
            "the screen for one ceremony is following another ceremony's key state"
        )
    }

    /**
     * The damaging half, and the reason this is a block rather than a cosmetic
     * mix-up. Both rungs disable their button while a signing of that kind is
     * running and un-failed, and a *completed* signing is un-failed forever --
     * so by kind alone, a parent that had certified one subgroup could never be
     * asked to certify another, and the rung sat there disabled with no way out.
     */
    @Test
    fun `a sibling's finished signings leave this ceremony's rungs open`() = runBlocking {
        // Driven in two emissions rather than one, so the assertion is about a
        // watcher that demonstrably ran. Asserting "still null" against a single
        // emission passes before the watcher has started, which is how a version
        // of this test passed against the bug it was written for.
        val signingSessions = MutableStateFlow(
            listOf(
                certificateSigning("mine", subgroupOf(firstKey)),
                keyStateSigning("myKeyState", dkgSessionId = "firstCeremony"),
            )
        )

        val viewModel = viewModel(signingSessions)
        viewModel.initiate()

        awaitLoaded(viewModel) {
            it.certificateSession?.id == "mine" && it.keyStateSession?.id == "myKeyState"
        }

        // Now the room holds only a sibling's, both finished -- which is every
        // parent that has already made one subgroup and is making its second.
        signingSessions.value = listOf(
            certificateSigning(
                "siblingCertificate",
                subgroupOf(secondKey),
                stage = FrostSigningStage.COMPLETE
            ),
            // A key state naming another ceremony: the sibling's, or this parent's
            // own from an adoption, which is signed in this room too.
            keyStateSigning(
                "siblingKeyState",
                dkgSessionId = "secondCeremony",
                stage = FrostSigningStage.COMPLETE
            ),
        )

        val loaded = awaitLoaded(viewModel) {
            it.certificateSession == null && it.keyStateSession == null
        }

        // Both rungs gate on these being null or failed, and a completed signing
        // is never failed -- so a sibling left in either of them shuts the rung
        // for good.
        assertNull(loaded.certificateSession)
        assertNull(loaded.keyStateSession)
    }

    private suspend fun load(
        signingSessions: List<LocalFrostSigningSession>,
        until: (DkgRitualUIState.Loaded) -> Boolean,
    ): DkgRitualUIState.Loaded {
        val viewModel = viewModel(MutableStateFlow(signingSessions))
        viewModel.initiate()
        return awaitLoaded(viewModel, until)
    }

    private suspend fun awaitLoaded(
        viewModel: DkgRitualViewModel,
        until: (DkgRitualUIState.Loaded) -> Boolean,
    ): DkgRitualUIState.Loaded = withTimeout(5_000) {
        viewModel.dkgRitualUIState.first {
            (it as? DkgRitualUIState.Loaded)?.let(until) == true
        }
    } as DkgRitualUIState.Loaded

    /** The screen is always showing the FIRST subgroup's ceremony. */
    private fun viewModel(
        signingSessions: MutableStateFlow<List<LocalFrostSigningSession>>
    ): DkgRitualViewModel {
        val ceremony = DkgSession(
            id = "firstCeremony",
            chatRoomId = parentChatRoomId,
            coordinatorPublicKey = me,
            userPublicKey = me,
            threshold = 2,
            participantCount = 2,
            hostPublicKey = me,
            round1Random = "66".repeat(32),
            round2AuxRandom = "77".repeat(32),
            stage = DkgRitualStage.COMPLETE,
            thresholdPublicKey = firstKey,
            parentChatRoomId = parentChatRoomId,
            subject = "Translators",
        )

        return DkgRitualViewModel(
            chatRoomId = parentChatRoomId,
            activeUserPublicKey = me,
            dkgSessionId = ceremony.id,
            initialDkgRitualUIState = DkgRitualUIState.Loading,
            activeIdentityStateFlow = MutableStateFlow(null),
            chatRepository = object : ChatRepository by ChatRepository.NO_OP_CHAT_REPOSITORY {
                override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? = room()
            },
            dkgRepository = object : DkgRepository by DkgRepository.NO_OP_DKG_REPOSITORY {
                override suspend fun roomDerivedBy(dkgSessionId: String): String? =
                    if (dkgSessionId == ceremony.id) subgroupOf(firstKey) else null

                override fun observeSessionById(sessionId: String): Flow<DkgSession?> =
                    flowOf(ceremony)

                override fun observeLatestSessionForChatRoom(
                    chatRoomId: String,
                    parentChatRoomId: String?,
                ): Flow<DkgSession?> = flowOf(ceremony)

                override fun observeSigningSessions(
                    chatRoomId: String
                ): Flow<List<LocalFrostSigningSession>> = signingSessions
            }
        )
    }

    /** The room id a ceremony's key derives, which is what a certificate names. */
    private fun subgroupOf(thresholdPublicKey: HexKey): String =
        press.mantra.compose.managers.SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

    private fun certificateSigning(
        id: String,
        subgroupChatRoomId: String,
        stage: FrostSigningStage = FrostSigningStage.COLLECTING_NONCES,
    ) = signing(
        id = id,
        stage = stage,
        event = Event(
            id = "ee".repeat(32),
            pubKey = me,
            createdAt = 0,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = firstKey,
                adminPublicKeys = listOf(me, peer),
                name = "Translators",
            ),
            content = subgroupChatRoomId,
            sig = "ff".repeat(64),
        )
    )

    private fun keyStateSigning(
        id: String,
        dkgSessionId: String,
        stage: FrostSigningStage = FrostSigningStage.COLLECTING_NONCES,
    ) = signing(
        id = id,
        stage = stage,
        event = Event(
            id = "ee".repeat(32),
            pubKey = me,
            createdAt = 0,
            kind = GroupKeyStateEvent.KIND,
            tags = GroupKeyStateEvent.assembleTags(
                chatRoomId = subgroupOf(firstKey),
                dkgSessionId = dkgSessionId,
            ),
            content = firstKey,
            sig = "ff".repeat(64),
        )
    )

    private fun signing(
        id: String,
        event: Event,
        stage: FrostSigningStage = FrostSigningStage.COLLECTING_NONCES,
    ) = LocalFrostSigningSession(
        session = FrostSigningSession(
            id = id,
            stage = stage,
            chatRoomId = parentChatRoomId,
            coordinatorPublicKey = me,
            userPublicKey = me,
            dkgSessionId = "firstCeremony",
            threshold = 2,
            participantCount = 2,
            signerId = 0,
        ),
        unorderedItems = listOf(
            FrostSigningItem(
                sessionId = id,
                itemIndex = 0,
                unsignedEventJson = event.toJson(),
                eventId = event.id,
                nonceRandom = "55".repeat(32),
            )
        ),
    )

    private fun room() = LocalChatRoom(
        chatRoom = ChatRoom(
            id = parentChatRoomId,
            userPublicKey = me,
            subject = "Ekklesia",
            description = null,
            mlsGroupState = "state",
        ),
        localParticipants = listOf(me, peer).map { publicKey ->
            LocalParticipant(
                participant = Participant(
                    participantPublicKey = publicKey,
                    chatRoomId = parentChatRoomId,
                    relayHint = null,
                ),
                profile = null,
            )
        },
    )
}
