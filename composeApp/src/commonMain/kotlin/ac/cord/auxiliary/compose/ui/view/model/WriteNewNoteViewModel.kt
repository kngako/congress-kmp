package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.content.ContentParser
import ac.cord.auxiliary.compose.ui.composable.widgets.content.ContentSegment
import ac.cord.auxiliary.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.cord.auxiliary.compose.ui.view.state.form.WriteNewNoteFormState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WriteNewNoteViewModel(
    val replyToNostrEventId: HexKey?,
    val quotedNostrEventId: HexKey?,

    initialWriteNewNoteUIState: WriteNewNoteUIState,
    val writeNewNoteFormState: WriteNewNoteFormState = WriteNewNoteFormState(),
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "WriteNewNoteViewModel"

        fun factory(
            replyToNostrEventId: HexKey?,
            quotedNostrEventId: HexKey?,
            initialWriteNewNoteUIState: WriteNewNoteUIState,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WriteNewNoteViewModel(
                    replyToNostrEventId = replyToNostrEventId,
                    quotedNostrEventId = quotedNostrEventId,
                    initialWriteNewNoteUIState = initialWriteNewNoteUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var writeNewNoteUIState: WriteNewNoteUIState by mutableStateOf(initialWriteNewNoteUIState)
        private set

    var mentionableProfiles = mutableStateListOf<Profile>()
        private set

    var mentionedProfiles = mutableStateListOf<Profile>()
        private set

    var atSymbolRange = mutableStateOf<TextRange?>(null)

    fun initiate() {
        observeActiveUserProfileWithFollowing()
        loadSearchableProfiles()
    }

    fun loadSearchableProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            mentionableProfiles.addAll(
                nostrRepository.searchableProfiles()
            )
        }
    }

    fun addProfileToMentionedProfiles(profile: Profile) {
        atSymbolRange.value?.let { range ->
            mentionedProfiles.add(profile)
            writeNewNoteFormState.textField.textFieldState.edit {
                val mentionProfile = "nostr:${
                    NProfile.create(
                        profile.publicKey,
                        null // TODO: Get the relay this profile was seen from...
                    )
                }"

                replace(
                    range.start,
                    range.end,
                    mentionProfile
                )
            }
        }
    }

    fun observeActiveUserProfileWithFollowing() {
        logger.d("observeActiveUserProfileWithFollowing")
        viewModelScope.launch(Dispatchers.IO) {
            val nostrEventBeingRepliedTo = replyToNostrEventId?.let {
                nostrRepository.observeLocalNostrEventById(
                    it
                ).firstOrNull()
            }

            val nostrEventBeingQuoted = quotedNostrEventId?.let {
                nostrRepository.observeLocalNostrEventById(
                    it
                ).firstOrNull()
            }

            val activePublicKey = SeedManager.activePublicKey().toHexKey()

            // TODO: Get note being replied too...
            // TODO: Get users being mentioned...
            nostrRepository.observeProfileWithFollowing(
                activePublicKey
            ).collect { profileWithFollowing ->
                writeNewNoteUIState = if (profileWithFollowing != null) {
                    WriteNewNoteUIState.InputPrompt(
                        localProfile = profileWithFollowing,
                        inReplyToNostrEvent = nostrEventBeingRepliedTo,
                        quotedNostrEvent = nostrEventBeingQuoted
                    )
                } else {
                    WriteNewNoteUIState.Error
                }
            }
        }
    }

    fun confirmNote(
        localProfile: LocalProfileWithFollowing,
        inReplyToNostrEvent: LocalNostrEvent?,
        quotedNostrEvent: LocalNostrEvent?
    ) {
        validateInput().let { isInputValid ->
            if (isInputValid) {
                writeNewNoteUIState =
                    WriteNewNoteUIState.ConfirmNote(
                        localProfile = localProfile,
                        inReplyToNostrEvent = inReplyToNostrEvent,
                        quotedNostrEvent = quotedNostrEvent,
                        text = writeNewNoteFormState.textField.textFieldState.text.toString(),
                    )
            }
        }

    }

    fun validateInput(): Boolean {
        if (writeNewNoteFormState.textField.textFieldState.text.isBlank()) {
            writeNewNoteFormState.textField.errorMessage.value = "Please input a note"
            return false
        } else {
            writeNewNoteFormState.textField.errorMessage.value = null
        }
        return true
    }

    fun createNewNote(
        onNostrEventPublished: () -> Unit,
        inReplyToNostrEvent: LocalNostrEvent? = null,
        quotedNostrEvent: LocalNostrEvent? = null,
    ) {
        logger.d { "createAccount" }
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val mentionedPublicKeys = ContentParser.parse(
                writeNewNoteFormState.textField.textFieldState.text.toString(),
            ).filterIsInstance<ContentSegment.NostrProfileSegment>().map { it.pubkey }

            nostrRepository.createNewTextNote(
                publicKey = SeedManager.activePublicKey().toHexKey(),
                mentionedPublicKeys = mentionedPublicKeys,
                textInput = writeNewNoteFormState.textField.textFieldState.text.toString(),
                inReplyToNostrEvent = inReplyToNostrEvent,
                quotedNostrEvent = quotedNostrEvent,
                onError = {
                    writeNewNoteUIState = WriteNewNoteUIState.Error
                },
                onSuccess = onNostrEventPublished
            )
        }
    }
}