package at.torch.compose.ui.view.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.repository.MarmotRepository
import at.torch.compose.ui.view.state.KeyPackageManagementUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class KeyPackageManagementViewModel(
    val activeUserPublicKey: HexKey,
    val nostrEventId: HexKey,
    initialActiveProfileUIState: KeyPackageManagementUIState,
    val marmotRepository: MarmotRepository
): ViewModel() {
    var keyPackageManagementUIState: KeyPackageManagementUIState by mutableStateOf(initialActiveProfileUIState)
        private set

    fun retryLoad() {
        keyPackageManagementUIState = KeyPackageManagementUIState.Loading
        loadNostrFeed()
    }

    fun loadNostrFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            keyPackageManagementUIState = KeyPackageManagementUIState.Loaded(
                keyPackageBundles = marmotRepository.getMarmotKeyPackageBundles(activeUserPublicKey)
            )
        }
    }

    fun publishNewKeyPackage() {
        viewModelScope.launch(Dispatchers.IO) {
            marmotRepository.publishMarmotKeyPackageBundle(
                publicKey = activeUserPublicKey,
                nsecPassword = "" // TODO: Implement nsecPassword logic...
            )

            keyPackageManagementUIState = KeyPackageManagementUIState.Loaded(
                keyPackageBundles = marmotRepository.getMarmotKeyPackageBundles(activeUserPublicKey)
            )
        }
    }

    companion object {
        const val TAG = "KeyPackageManagementViewModel"

        fun factory(
            nostrEventId: HexKey,
            activeUserPublicKey: HexKey,
            initialActiveProfileUIState: KeyPackageManagementUIState = KeyPackageManagementUIState.Loading,
            marmotRepository: MarmotRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KeyPackageManagementViewModel(
                    nostrEventId = nostrEventId,
                    initialActiveProfileUIState = initialActiveProfileUIState,
                    marmotRepository = marmotRepository,
                    activeUserPublicKey = activeUserPublicKey
                )
            }
        }
    }
}