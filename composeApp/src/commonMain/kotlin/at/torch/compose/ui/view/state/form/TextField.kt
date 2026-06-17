package at.torch.compose.ui.view.state.form

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class TextField(
    val textFieldState: TextFieldState = TextFieldState(),
    val errorMessage: MutableState<String?> = mutableStateOf(null)
)
