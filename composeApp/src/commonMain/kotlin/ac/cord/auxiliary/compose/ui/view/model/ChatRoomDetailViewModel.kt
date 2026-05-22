package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.repository.NostrRepository
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger

class ChatRoomDetailViewModel(
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "DirectMessageDetailViewModel"

        fun factory(
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailViewModel(
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun queueSynchronization() {
        logger.d { "queueSynchronization" }
    }
}