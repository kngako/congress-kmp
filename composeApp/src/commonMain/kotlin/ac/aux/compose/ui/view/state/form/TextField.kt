package ac.aux.compose.ui.view.state.form

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class TextField(
    val text: MutableState<String> = mutableStateOf(""),
    val errorMessage: MutableState<String?> = mutableStateOf(null)
)
