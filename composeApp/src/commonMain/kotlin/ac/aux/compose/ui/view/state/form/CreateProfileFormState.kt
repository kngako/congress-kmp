package ac.aux.compose.ui.view.state.form

data class CreateProfileFormState(
    val nameField: TextField = TextField(),
    val biographyField: TextField = TextField()
)