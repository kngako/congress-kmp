package at.torch.compose.ui.view.state.form

data class ChatRoomCreationFormState(
    val nameField: TextField = TextField(),
    val descriptionField: TextField = TextField()
)