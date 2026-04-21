package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.SignInToProfileViewModel
import ac.cord.auxiliary.compose.ui.view.state.SignInToProfileUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInToProfileScreen(
    initialSignInToProfileUIState: SignInToProfileUIState = SignInToProfileUIState.InputPrompt,
    nostrRepository: NostrRepository
) {
    val signInToProfileViewModel: SignInToProfileViewModel =  viewModel (
        factory = SignInToProfileViewModel.factory(
            initialSignInToProfileUIState,
            nostrRepository
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

                when (val signInToProfileUIState = signInToProfileViewModel.signInToProfileUIState.value) {
                    is SignInToProfileUIState.InputPrompt -> {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = signInToProfileViewModel.signInToProfileFormState.textField.text.value,
                            onValueChange = {
                                signInToProfileViewModel.signInToProfileFormState.textField.text.value = it
                            },
                            isError = signInToProfileViewModel.signInToProfileFormState.textField.errorMessage.value != null,
                            supportingText = signInToProfileViewModel.signInToProfileFormState.textField.errorMessage.value?.let {
                                {
                                    Text(
                                        text = it,
                                        modifier = Modifier.padding(
                                            bottom = 8.dp
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            label = {
                                Text(
                                    text = "nsec, npub, nip-05 (static address)",
                                    maxLines = 1,
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "Enter the nsec or npub (read only) that you want to sign in as",
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = "Write note"
                                )
                            }
                        )

                        Button(
                            onClick = {
                                signInToProfileViewModel.signInToProfileUIState.value = signInToProfileViewModel.validateCredential().let { credentialEntity ->
                                    when (credentialEntity) {
                                        is NSec -> {
                                            SignInToProfileUIState.ConfirmNsecSignIn(
                                                nsec = signInToProfileViewModel.signInToProfileFormState.textField.text.value,
                                                hexKey = credentialEntity.hex
                                            )
                                        }
                                        is NPub -> {
                                            SignInToProfileUIState.ConfirmNpubSignIn(
                                                npub = signInToProfileViewModel.signInToProfileFormState.textField.text.value,
                                                hexKey = credentialEntity.hex
                                            )
                                        }
                                        else -> {
                                            SignInToProfileUIState.Error
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(
                                "Next"
                            )
                            Icon(
                                Icons.Default.NavigateNext,
                                contentDescription = "Next"
                            )
                        }

                    }
                    is SignInToProfileUIState.ConfirmNpubSignIn-> {

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Sign in to Npub",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = signInToProfileUIState.npub,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = signInToProfileUIState.hexKey,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )


                        Spacer(
                            modifier = Modifier.weight(4f)
                        )



                        Text(
                            text = "This will give you read only access to the profile.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )

                        if (signInToProfileViewModel.isActionPending.value) {
                            LoadingIndicator(
                                modifier = Modifier
                            )
                        } else {
                            Button(
                                onClick = {
                                    signInToProfileViewModel.signInToAccount(
                                        onError = {
                                            signInToProfileViewModel.signInToProfileUIState.value =
                                                SignInToProfileUIState.Error
                                        }
                                    )
                                }
                            ) {
                                Text(
                                    text = "Sign In"
                                )
                            }
                        }

                    }
                    is SignInToProfileUIState.ConfirmNsecSignIn-> {

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Sign in to nsec",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = "Be sure to keep this nsec safe.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )


                        Spacer(
                            modifier = Modifier.weight(4f)
                        )



                        Text(
                            text = "This will give you write access to the profile.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )

                        if (signInToProfileViewModel.isActionPending.value) {
                            LoadingIndicator(
                                modifier = Modifier
                            )
                        } else {
                            Button(
                                onClick = {
                                    signInToProfileViewModel.signInToAccount(
                                        onError = {
                                            signInToProfileViewModel.signInToProfileUIState.value =
                                                SignInToProfileUIState.Error
                                        }
                                    )
                                }
                            ) {
                                Text(
                                    text = "Sign In"
                                )
                            }
                        }

                    }
                    is SignInToProfileUIState.Error -> {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Something went wrong and we were unable to sign in to your provided profile. Please try again later.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SignInToProfileScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            SignInToProfileScreen(
                initialSignInToProfileUIState =
                    SignInToProfileUIState.InputPrompt,
//                    SignInToProfileUIState.Error,
//                    SignInToProfileUIState.ConfirmNpubSignIn(
//                        npub = "npub1uh366qnj68a7atvwgwdy4569xd6v5jtferftapqztwjwlzh9553q47pqsx",
//                        hexKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522"
//                    ),
//                    SignInToProfileUIState.ConfirmNsecSignIn(
//                        nsec = "nsecthingsaretobekeptsecret",
//                        hexKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522"
//                    ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY)
        }
    }
}