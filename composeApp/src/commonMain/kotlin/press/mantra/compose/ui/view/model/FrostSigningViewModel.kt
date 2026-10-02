package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.nostr.frost.FrostSigningEvents
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.FrostSigningUIState

class FrostSigningViewModel(
    val chatRoomId: String,
    val sessionId: String,
    val activeUserPublicKey: HexKey,
    initialFrostSigningUIState: FrostSigningUIState,
    val chatRepository: ChatRepository,
    val frostSigningRepository: FrostSigningRepository,
    val mantraRepository: MantraRepository,
    val nostrRepository: NostrRepository,
): ViewModel() {

    var frostSigningUIState: FrostSigningUIState by mutableStateOf(initialFrostSigningUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /**
     * Loads the room, then watches the session for as long as the screen lives.
     *
     * A session moves on messages arriving from other members, so a screen that
     * read it once would sit still while the rest of the group signed around it.
     */
    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (localChatRoom == null) {
                frostSigningUIState = FrostSigningUIState.Error("Couldn't find the group")
                return@launch
            }

            frostSigningUIState = FrostSigningUIState.Loaded(localChatRoom = localChatRoom)

            combine(
                frostSigningRepository.observeSessionById(sessionId),
                frostSigningRepository.observeMessages(sessionId),
                frostSigningRepository.observeItems(sessionId)
            ) { session, messages, items -> Triple(session, messages, items) }
                .collect { (session, messages, items) ->
                    frostSigningUIState = FrostSigningUIState.Loaded(
                        localChatRoom = localChatRoom,
                        session = session,
                        items = items,
                        offeredNonce = messages
                            .filter { it.kind == FrostSigningEvents.NONCE }
                            .map { it.signerPublicKey }
                            .toSet(),
                        signed = messages
                            .filter { it.kind == FrostSigningEvents.PARTIAL_SIGNATURE }
                            .map { it.signerPublicKey }
                            .toSet(),
                        names = namesIn(proposedEvents(items))
                    )
                }
        }
    }

    /**
     * Whatever the proposed events refer to, as words.
     *
     * Only the kinds that need it, and only what this device already holds. A
     * key with no profile and an id with no row are both left out, and
     * `ProposedEvent.summarize` falls back to a short hash -- which is truthful
     * rather than a gap: the device has not seen the thing.
     *
     * A project's own id resolves to nothing while it is being proposed,
     * because nothing has applied it yet. That is why a membership says the
     * version first.
     */
    private suspend fun namesIn(events: List<Event>): Map<HexKey, String> = buildMap {
        suspend fun person(publicKey: HexKey) {
            if (containsKey(publicKey)) return
            nostrRepository.getProfileWithPublicKey(publicKey)?.let {
                put(publicKey, it.humanReadableNameOrPubkey())
            }
        }

        suspend fun project(projectId: HexKey?) {
            if (projectId == null || containsKey(projectId)) return
            mantraRepository.getProject(projectId)?.let { put(projectId, it.name) }
        }

        events.forEach { event ->
            when (event.kind) {
                ProjectEvent.KIND -> {
                    val proposed = ProjectEvent(
                        event.id, event.pubKey, event.createdAt,
                        event.tags, event.content, event.sig
                    )
                    proposed.creator()?.let { person(it) }
                    proposed.owners().forEach { person(it) }
                }

                ProjectArtifactVersionEvent.KIND -> {
                    val membership = ProjectArtifactVersionEvent(
                        event.id, event.pubKey, event.createdAt,
                        event.tags, event.content, event.sig
                    )
                    membership.artifactVersionId()?.let { versionId ->
                        mantraRepository.getArtifactVersion(versionId)?.let { version ->
                            val artifact = mantraRepository.getArtifact(version.artifactId)
                            put(
                                versionId,
                                listOfNotNull(artifact?.name, version.versionLabel)
                                    .joinToString(" ")
                            )
                        }
                    }
                    project(membership.projectId())
                }

                ProjectTranslationArtifactVersionEvent.KIND -> {
                    val membership = ProjectTranslationArtifactVersionEvent(
                        event.id, event.pubKey, event.createdAt,
                        event.tags, event.content, event.sig
                    )
                    membership.translationArtifactVersionId()?.let { translationId ->
                        mantraRepository.getTranslation(translationId)?.let {
                            put(translationId, it.name)
                        }
                    }
                    project(membership.projectId())
                }
            }
        }
    }

    /** The events the group is being asked to sign, for showing them before they agree. */
    fun proposedEvents(items: List<FrostSigningItem>): List<Event> =
        items.mapNotNull { Event.fromJsonOrNull(it.unsignedEventJson) }

    fun signedEvents(items: List<FrostSigningItem>): List<Event> =
        frostSigningRepository.signedEvents(items)

    fun approve(onDone: () -> Unit) {
        val state = frostSigningUIState as? FrostSigningUIState.Loaded ?: return
        val sessionId = state.session?.id ?: return

        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            frostSigningRepository.approve(
                localChatRoom = state.localChatRoom,
                sessionId = sessionId
            )

            isActionPending.value = false

            viewModelScope.launch(Dispatchers.Main) { onDone.invoke() }
        }
    }

    fun decline(onDone: () -> Unit) {
        val state = frostSigningUIState as? FrostSigningUIState.Loaded ?: return
        val sessionId = state.session?.id ?: return

        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            frostSigningRepository.decline(
                localChatRoom = state.localChatRoom,
                sessionId = sessionId
            )

            isActionPending.value = false

            viewModelScope.launch(Dispatchers.Main) { onDone.invoke() }
        }
    }

    companion object {
        private const val TAG = "FrostSigningViewModel"

        fun factory(
            chatRoomId: String,
            sessionId: String,
            activeUserPublicKey: HexKey,
            initialFrostSigningUIState: FrostSigningUIState = FrostSigningUIState.Loading,
            chatRepository: ChatRepository,
            frostSigningRepository: FrostSigningRepository,
            mantraRepository: MantraRepository,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FrostSigningViewModel(
                    chatRoomId = chatRoomId,
                    sessionId = sessionId,
                    activeUserPublicKey = activeUserPublicKey,
                    initialFrostSigningUIState = initialFrostSigningUIState,
                    chatRepository = chatRepository,
                    frostSigningRepository = frostSigningRepository,
                    mantraRepository = mantraRepository,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}
