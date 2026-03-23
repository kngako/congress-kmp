package ac.aux.compose.ui.view.model

import ac.aux.compose.ui.view.state.FeedListUIState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

class FeedListViewModel(
    initialFeedListUIState: FeedListUIState,
): ViewModel() {
    var feedListUIState: FeedListUIState by mutableStateOf(initialFeedListUIState)
        private set

    fun retryLoad() {
        feedListUIState = FeedListUIState.Loading
        loadRecipientListData()
    }

    fun loadRecipientListData() {
        viewModelScope.launch {
            // TODO: Load Dummy data...
        }
    }


    companion object {
        const val TAG = "RecipientListViewModel"

        fun factory(
            initialFeedListUIState: FeedListUIState = FeedListUIState.Loading,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedListViewModel(
                    initialFeedListUIState = initialFeedListUIState,
                )
            }
        }
    }
}