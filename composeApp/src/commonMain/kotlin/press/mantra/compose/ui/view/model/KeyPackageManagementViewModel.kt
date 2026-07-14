package press.mantra.compose.ui.view.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.MarmotKeyPackageBundle
import press.mantra.compose.repository.MarmotRepository
import press.mantra.compose.ui.view.state.KeyPackageManagementUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class KeyPackageManagementViewModel(
    val activeUserPublicKey: HexKey,
    val nostrEventId: HexKey,
    initialActiveProfileUIState: KeyPackageManagementUIState,
    val marmotRepository: MarmotRepository
): ViewModel() {
    var keyPackageManagementUIState: KeyPackageManagementUIState by mutableStateOf(initialActiveProfileUIState)
        private set

    init {
        loadNostrFeed()
    }

    fun loadNostrFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            marmotRepository.observeMarmotKeyPackageBundles(activeUserPublicKey).distinctUntilChanged().collect { keyPackageBundles ->
                keyPackageManagementUIState = KeyPackageManagementUIState.Loaded(
                    keyPackageBundles = keyPackageBundles
                )
            }
        }
    }

    fun publishNewKeyPackage() {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Rotate all other keys...
            marmotRepository.publishMarmotKeyPackageBundle(
                publicKey = activeUserPublicKey,
                nsecPassword = "" // TODO: Implement nsecPassword logic...
            )
        }
    }

    fun rotateKeyPackage(marmotKeyPackageBundle: MarmotKeyPackageBundle) {
        viewModelScope.launch(Dispatchers.IO) {
            marmotRepository.rotateMarmotKeyPackageBundle(marmotKeyPackageBundle)
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