package at.torch.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.repository.MarmotRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.view.state.CreateProfileUIState
import at.torch.compose.ui.view.state.form.CreateProfileFormState
import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nostrPublicKey
import fr.acinq.phoenix.managers.nsecPassword
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class CreateProfileViewModel(
    val initialCreateProfileUIState: CreateProfileUIState,
    val createProfileFormState: CreateProfileFormState = CreateProfileFormState(),
    val nostrRepository: NostrRepository,
    val marmotRepository: MarmotRepository
): ViewModel() {
    companion object {
        private const val TAG = "CreateAccountViewModel"

        fun factory(
            initialCreateProfileUIState: CreateProfileUIState,
            nostrRepository: NostrRepository,
            marmotRepository: MarmotRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateProfileViewModel(
                    initialCreateProfileUIState,
                    nostrRepository = nostrRepository,
                    marmotRepository = marmotRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var createProfileUIState: MutableState<CreateProfileUIState> = mutableStateOf(
        initialCreateProfileUIState
    )

    fun validateInput(): Boolean {
        if (createProfileFormState.nameField.textFieldState.text.isBlank()) {
            createProfileFormState.nameField.errorMessage.value = "Please input a display name"
            return false
        } else {
            createProfileFormState.nameField.errorMessage.value = null
        }

        if (createProfileFormState.biographyField.textFieldState.text.isBlank()) {
            createProfileFormState.biographyField.errorMessage.value = "Please input a biography"
            return false
        } else {
            createProfileFormState.biographyField.errorMessage.value = null
        }

        return true
    }


    fun createAccount(
        writeSeed: (List<String>) -> Unit
//        onNavigateToUnsignedProfile: (UnsignedProfileRoute) -> Unit,
//        onNavigateToUnannouncedProfile: (UnannouncedProfileRoute) -> Unit
    ) {
        logger.d { "createAccount" }
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            logger.e("error when creating new wallet: ", e)
            throw e
        }) {
            // TODO: Generate a new profile...
            logger.d("generating new wallet...")
            val entropy = Lightning.randomBytes(16)
            val mnemonics = MnemonicCode.toMnemonics(
                entropy = entropy,
                wordlist = MnemonicLanguage.English.wordlist()
            )
            writeSeed(mnemonics)

            val localKeyManager = LocalKeyManager(
                seed = MnemonicCode.toSeed(mnemonics, "").byteVector(),
                chain = Chain.Mainnet,
                remoteSwapInExtendedPublicKey =  NodeParamsManager.remoteSwapInXpub
            )

            val pubkey = localKeyManager.nostrPublicKey()

            nostrRepository.createNewProfile(
                pubkey,
                name = createProfileFormState.nameField.textFieldState.text.toString(),
                biography = createProfileFormState.biographyField.textFieldState.text.toString()
            )

            marmotRepository.publishMarmotKeyPackageBundle(
                publicKey = pubkey,
                nsecPassword = localKeyManager.nsecPassword()
            )
        }
    }

    fun wipeDatabase(
        onNavigateToEndThis: () -> Unit
    ) {
        viewModelScope.launch {
            nostrRepository.wipeDatabase()
            onNavigateToEndThis.invoke()
        }
    }
}