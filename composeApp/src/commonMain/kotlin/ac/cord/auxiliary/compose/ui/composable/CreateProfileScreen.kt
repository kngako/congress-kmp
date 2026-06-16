package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.theme.BluePill
import ac.cord.auxiliary.compose.ui.theme.RedPill
import ac.cord.auxiliary.compose.ui.view.model.CreateProfileViewModel
import ac.cord.auxiliary.compose.ui.view.state.CreateProfileUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Chain
import fr.acinq.phoenix.managers.WalletManager

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateProfileScreen(
    initialCreateProfileUIState: CreateProfileUIState = CreateProfileUIState.Declaration,
    onNavigateToSocialPreconditionRoute: () -> Unit,
    onNavigateToEndThis: () -> Unit,
    nostrRepository: NostrRepository,
    writeSeed: (List<String>) -> Unit,
    walletManager: WalletManager
) {
    val createProfileViewModel: CreateProfileViewModel =  viewModel (
        factory = CreateProfileViewModel.factory(
            initialCreateProfileUIState,
            nostrRepository,
            walletManager = walletManager
        )
    )
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {

            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    10.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                when (val createAccountUIState = createProfileViewModel.createProfileUIState.value) {
                    is CreateProfileUIState.Declaration -> {

                        Text(
                            "Create Profile",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            "You are about to create a NOSTR profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            "It is cryptographical secure, and decentralized, putting you in total control of your digital profile",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {

                                createProfileViewModel.createProfileUIState.value = CreateProfileUIState.InputPrompt
                            }
                        ) {
                            Text(
                                text = "Start"
                            )
                        }
                    }
                    is CreateProfileUIState.InputPrompt -> {
                        Text(
                            "Create Profile",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = createProfileViewModel.createProfileFormState.nameField.textFieldState,
//                            value = createProfileViewModel.createProfileFormState.nameField.text.value,
//                            onValueChange = {
//                                createProfileViewModel.createProfileFormState.nameField.text.value = it
//                            },
                            isError = createProfileViewModel.createProfileFormState.nameField.errorMessage.value != null,
                            supportingText = createProfileViewModel.createProfileFormState.nameField.errorMessage.value?.let {
                                {
                                    Text(
                                        text = it,
                                        modifier = Modifier.padding(
                                            bottom = 8.dp
                                        )
                                    )
                                }
                            },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                            label = {
                                Text(
                                    text = "Name (eg. Alan Turin)",
                                    maxLines = 1,
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "Enter the name you want to use for your profile",
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Badge,
                                    contentDescription = "Name"
                                )
                            }
                        )

                        Text(
                            text = "This will be the display name for your profile and also important for search.",
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = createProfileViewModel.createProfileFormState.biographyField.textFieldState,
                            isError = createProfileViewModel.createProfileFormState.biographyField.errorMessage.value != null,
                            supportingText = createProfileViewModel.createProfileFormState.biographyField.errorMessage.value?.let {
                                {
                                    Text(
                                        text = it,
                                        modifier = Modifier.padding(
                                            bottom = 8.dp
                                        )
                                    )
                                }
                            },
                            lineLimits = TextFieldLineLimits.MultiLine(4),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                            label = {
                                Text(
                                    text = "Introduce yourself",
                                    maxLines = 1,

                                )
                            },
                            placeholder = {
                                Text(
                                    text = "What should people know about you?",
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = "Name"
                                )
                            }
                        )

                        Text(
                            text = "This will be shown when people open your profile.",
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {

                                createProfileViewModel.validateInput().let { isInputValid ->
                                    if (isInputValid) {
                                        createProfileViewModel.createProfileUIState.value =
                                            CreateProfileUIState.ConfirmInput(
                                                name = createProfileViewModel.createProfileFormState.nameField.textFieldState.text.toString(),
                                                bio = createProfileViewModel.createProfileFormState.biographyField.textFieldState.text.toString()
                                            )
                                    }
                                }


                            }
                        ) {
                            Text(
                                "Next"
                            )
                        }

                    }
                    is CreateProfileUIState.ConfirmInput -> {

                        Text(
                            text = "Name",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Text(
                            text = createAccountUIState.name,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Bio",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Text(
                            text = createAccountUIState.bio,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )



                        Text(
                            text = "The above will be your profile.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "You will be in full control of this profile. If you would like to use it for the long term please remember to backup the profile/keys.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )

                        if (createProfileViewModel.isActionPending.value) {
                            LoadingIndicator(
                                modifier = Modifier
                            )
                        } else {
                            Button(
                                onClick = {
                                    createProfileViewModel.createAccount(writeSeed)
                                }
                            ) {
                                Text(
                                    text = "Create Profile"
                                )
                            }
                        }

                    }
                    is CreateProfileUIState.Error -> {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Something went wrong and we were unable to sign you up. Please try again later.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is CreateProfileUIState.ProfileReady -> {

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Profile is ready",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "After this there is no turning back."
                        )

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RedPill,
                                contentColor = Color.White
                            ),
                            onClick = {

                                onNavigateToSocialPreconditionRoute.invoke()
                            }
                        ) {
                            Text(
                                text = "See how deep the rabbit hole goes"
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(20.dp)
                        )

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BluePill,
                                contentColor = Color.DarkGray
                            ),
                            onClick = {
                                // TODO: Delete everything and close the app
                                createProfileViewModel.wipeDatabase {
                                    onNavigateToEndThis.invoke()
                                }
                            }
                        ) {
                            Text(
                                text = "end this"
                            )
                        }
                        Spacer(
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun CreateAccountScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            CreateProfileScreen(
                walletManager = WalletManager(Chain.Mainnet),
                initialCreateProfileUIState =
//                    CreateProfileUIState.InputPrompt,
//                    CreateProfileUIState.Error,
//                    CreateProfileUIState.ConfirmInput(
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
//                    CreateProfileUIState.UnsignedProfile(
//                        unsignedNostrEvent = UnsignedNostrEvent(
//                            kind = 0,
//                            content = "Something here",
//                            tags = emptyArray(),
//                        ),
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
//                    CreateProfileUIState.UnbroadcastedProfile(
//                        nostrEvent = NostrEvent(
//                            id = "",
//                            pubKey = "",
//                            kind = 0,
//                            content = "Something here",
//                            tags = emptyArray(),
//                            sig = ""
//                        ),
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
                    CreateProfileUIState.ProfileReady(
                        profile = Profile(
                            publicKey = "",
                            nostrEventId = "",
                            displayName = "Alan Turing",
                            about = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
                        )
                    ),
                onNavigateToSocialPreconditionRoute = {},
                onNavigateToEndThis = {},
                writeSeed = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}