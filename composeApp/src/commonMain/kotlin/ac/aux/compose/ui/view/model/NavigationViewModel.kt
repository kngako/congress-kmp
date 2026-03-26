package ac.aux.compose.ui.view.model

import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    val tempDevelopmentKeyPair = KeyPair(
        privKey = Random(21_000_000L).nextBytes(32)
    )
    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeProfile()
    }

    private val logger = Logger.withTag(TAG)



    private fun observeProfile() {
        viewModelScope.launch {
            nostrRepository.observeProfile(tempDevelopmentKeyPair.pubKey.toHexKey()).map { localProfile ->

                _navigationUIState.getAndUpdate {
                    if (localProfile == null) {
                        NavigationUIState.Landing
                    } else if (localProfile.profile != null) {
                        NavigationUIState.ProfileLoaded(
                            profile = localProfile.profile
                        )
                    } else if (localProfile.nostrEvent != null) {
                        NavigationUIState.UnbroadcastedProfile(
                            nostrEvent = localProfile.nostrEvent
                        )
                        // TODO: Support unsignedNostrEvent with pubKey so we can have multi account support...
//        }  else if (localProfile.nostrEvent != null) {
//            NavigationUIState.UnbroadcastedProfile(
//                nostrEvent = localProfile.nostrEvent
//            )
                    } else {
                        NavigationUIState.Error
                    }
                }
            }.catch {
                NavigationUIState.Error
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationUIState.Landing)
        }
    }

}