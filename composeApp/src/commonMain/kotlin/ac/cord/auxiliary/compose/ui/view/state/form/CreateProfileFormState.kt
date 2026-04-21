package ac.cord.auxiliary.compose.ui.view.state.form

data class CreateProfileFormState(
    val nameField: TextField = TextField(),
    val biographyField: TextField = TextField()
)