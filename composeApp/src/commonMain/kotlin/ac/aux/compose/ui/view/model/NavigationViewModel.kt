package ac.aux.compose.ui.view.model

import ac.aux.compose.managers.SeedManager
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val logger = Logger.withTag(TAG)

    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeProfile()
    }




    private fun observeProfile() {
        logger.i("observeProfile")
        viewModelScope.launch {
            delay(2_100) // Looking busy...
            _navigationUIState.getAndUpdate {
                NavigationUIState.Landing
            }

            logger.i("Navigation UI State is Landing")
            val publicKey = SeedManager.activePublicKey().toHexKey()
            logger.i("Observing: $publicKey")
            nostrRepository.observeProfile(
                publicKey = publicKey
            ).collect { localProfile ->
                logger.i("Local Profile: $localProfile")
                _navigationUIState.getAndUpdate {
                    if (localProfile == null) {
                        logger.i("We need to be on the landing screen so user creates account")
                        NavigationUIState.Landing
                    } else if (localProfile.profile != null) {
                        logger.i("We have a profile: ${localProfile.profile}")
                        NavigationUIState.ProfileLoaded(
                            profile = localProfile.profile
                        )
                    } else if (localProfile.nostrEvent != null) {
                        logger.i("Nostr event with the profile needs to be announced")
                        NavigationUIState.UnannouncedProfile(
                            nostrEvent = localProfile.nostrEvent
                        )
                    } else {
                        logger.i("We have an unsigned profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnsignedProfile(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    }
                }
            }
        }
    }

}