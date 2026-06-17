package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.CreateProfileUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.cord.auxiliary.compose.ui.view.state.form.CreateProfileFormState
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.managers.nostrPrivateKey
import fr.acinq.phoenix.managers.nostrPublicKey
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class CreateProfileViewModel(
    val initialCreateProfileUIState: CreateProfileUIState,
    val createProfileFormState: CreateProfileFormState = CreateProfileFormState(),
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "CreateAccountViewModel"

        fun factory(
            initialCreateProfileUIState: CreateProfileUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateProfileViewModel(
                    initialCreateProfileUIState,
                    nostrRepository = nostrRepository
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


    public fun createAccount(
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
            logger.d("NostrKey: $pubkey")
            nostrRepository.createNewProfile(
                pubkey,
                name = createProfileFormState.nameField.textFieldState.text.toString(),
                biography = createProfileFormState.biographyField.textFieldState.text.toString()
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