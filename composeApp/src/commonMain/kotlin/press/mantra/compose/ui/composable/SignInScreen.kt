package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
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
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.be_sure_to_keep_this_nsec_safe
import mantra.composeapp.generated.resources.enter_the_nsec_or_npub_read_only_that_you
import mantra.composeapp.generated.resources.next
import mantra.composeapp.generated.resources.nsec_npub_nip_05_static_address
import mantra.composeapp.generated.resources.sign_in
import mantra.composeapp.generated.resources.sign_in_to_nsec
import mantra.composeapp.generated.resources.sign_in_with_an_npub
import mantra.composeapp.generated.resources.something_went_wrong_and_we_were_unable_to
import mantra.composeapp.generated.resources.this_will_give_you_read_only_access_to_the
import mantra.composeapp.generated.resources.this_will_give_you_write_access_to_the
import androidx.compose.material3.SnackbarHost
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInToProfileScreen(
    initialSignInToProfileUIState: press.mantra.compose.ui.view.state.SignInToProfileUIState = press.mantra.compose.ui.view.state.SignInToProfileUIState.InputPrompt,
    nostrRepository: press.mantra.compose.repository.NostrRepository
) {
    val signInToProfileViewModel: press.mantra.compose.ui.view.model.SignInToProfileViewModel =  viewModel (
        factory = press.mantra.compose.ui.view.model.SignInToProfileViewModel.factory(
            initialSignInToProfileUIState,
            nostrRepository
        )
    )
    // imePadding: this screen has a text field, and without it the software
    // keyboard covers whatever is being typed into.
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        modifier = Modifier.imePadding()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {

            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    MaterialTheme.spacing.space125
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space250)
            ) {

                when (val signInToProfileUIState = signInToProfileViewModel.signInToProfileUIState.value) {
                    is press.mantra.compose.ui.view.state.SignInToProfileUIState.InputPrompt -> {
                        Text(
                            stringResource(Res.string.sign_in),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = signInToProfileViewModel.signInToProfileFormState.textFieldForm.textFieldState,
                            isError = signInToProfileViewModel.signInToProfileFormState.textFieldForm.errorMessage.value != null,
                            supportingText = signInToProfileViewModel.signInToProfileFormState.textFieldForm.errorMessage.value?.let {
                                {
                                    Text(
                                        text = it,
                                        modifier = Modifier.padding(
                                            bottom = MaterialTheme.spacing.compactPadding
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
                                    text = stringResource(Res.string.nsec_npub_nip_05_static_address),
                                    maxLines = 1,
                                )
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(Res.string.enter_the_nsec_or_npub_read_only_that_you),
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
                                            press.mantra.compose.ui.view.state.SignInToProfileUIState.ConfirmNsecSignIn(
                                                nsec = signInToProfileViewModel.signInToProfileFormState.textFieldForm.textFieldState.text.toString(),
                                                hexKey = credentialEntity.hex
                                            )
                                        }
                                        is NPub -> {
                                            press.mantra.compose.ui.view.state.SignInToProfileUIState.ConfirmNpubSignIn(
                                                npub = signInToProfileViewModel.signInToProfileFormState.textFieldForm.textFieldState.text.toString(),
                                                hexKey = credentialEntity.hex
                                            )
                                        }
                                        else -> {
                                            press.mantra.compose.ui.view.state.SignInToProfileUIState.Error
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(
                                stringResource(Res.string.next)
                            )
                            Icon(
                                Icons.Default.NavigateNext,
                                contentDescription = "Next"
                            )
                        }

                    }
                    is press.mantra.compose.ui.view.state.SignInToProfileUIState.ConfirmNpubSignIn-> {

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(Res.string.sign_in_with_an_npub),
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
                            text = stringResource(Res.string.this_will_give_you_read_only_access_to_the),
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
                                    signInToProfileViewModel.signInToAccount()
                                }
                            ) {
                                Text(
                                    text = stringResource(Res.string.sign_in)
                                )
                            }
                        }

                    }
                    is press.mantra.compose.ui.view.state.SignInToProfileUIState.ConfirmNsecSignIn-> {

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(Res.string.sign_in_to_nsec),
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = stringResource(Res.string.be_sure_to_keep_this_nsec_safe),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )


                        Spacer(
                            modifier = Modifier.weight(4f)
                        )



                        Text(
                            text = stringResource(Res.string.this_will_give_you_write_access_to_the),
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
                                    signInToProfileViewModel.signInToAccount()
                                }
                            ) {
                                Text(
                                    text = stringResource(Res.string.sign_in)
                                )
                            }
                        }

                    }
                    is press.mantra.compose.ui.view.state.SignInToProfileUIState.Error -> {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(Res.string.something_went_wrong_and_we_were_unable_to),
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
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            SignInToProfileScreen(
                initialSignInToProfileUIState =
                    press.mantra.compose.ui.view.state.SignInToProfileUIState.InputPrompt,
//                    SignInToProfileUIState.Error,
//                    SignInToProfileUIState.ConfirmNpubSignIn(
//                        npub = "npub1uh366qnj68a7atvwgwdy4569xd6v5jtferftapqztwjwlzh9553q47pqsx",
//                        hexKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522"
//                    ),
//                    SignInToProfileUIState.ConfirmNsecSignIn(
//                        nsec = "nsecthingsaretobekeptsecret",
//                        hexKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522"
//                    ),
                nostrRepository = press.mantra.compose.repository.NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}