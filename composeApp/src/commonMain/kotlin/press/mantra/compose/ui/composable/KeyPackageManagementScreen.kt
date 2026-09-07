package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Unpublished
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import press.mantra.compose.database.model.MarmotKeyPackageBundle
import press.mantra.compose.extensions.toFormattedTimeAndDateString
import press.mantra.compose.repository.MarmotRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.view.model.KeyPackageManagementViewModel
import press.mantra.compose.ui.view.state.KeyPackageManagementUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.key_package_management
import mantra.composeapp.generated.resources.publish_new_key_package
import mantra.composeapp.generated.resources.something_went_wrong
import mantra.composeapp.generated.resources.we_couldn_t_find_the_local_profile_please
import mantra.composeapp.generated.resources.key_packages

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KeyPackageManagementScreen(
    initialActiveProfileUIState: KeyPackageManagementUIState = KeyPackageManagementUIState.Loading,
    activeUserPublicKey: HexKey,
    nostrEventId: HexKey,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    marmotRepository: MarmotRepository
) {

    val keyPackageManagementViewModel: KeyPackageManagementViewModel = viewModel(
        factory = KeyPackageManagementViewModel.factory(
            nostrEventId = nostrEventId,
            initialActiveProfileUIState = initialActiveProfileUIState,
            marmotRepository = marmotRepository,
            activeUserPublicKey = activeUserPublicKey
        ),
    )

    when(val keyPackageManagementUIState = keyPackageManagementViewModel.keyPackageManagementUIState) {
        KeyPackageManagementUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(Res.string.something_went_wrong))
            }
        }
        is KeyPackageManagementUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(Res.string.key_package_management)
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    onNavigateBack.invoke()
                                }
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Button(
                                onClick = {
                                    keyPackageManagementViewModel.publishNewKeyPackage()
                                }
                            ) {
                                Icon(
                                    Icons.Default.LockReset,
                                    contentDescription = "Publish new key package"
                                )

                                Spacer(
                                    modifier = Modifier.width(MaterialTheme.spacing.space125)
                                )

                                Text(
                                    text = stringResource(Res.string.publish_new_key_package)
                                )
                            }
                        }
                        item {
                            Text(
                                stringResource(Res.string.key_packages, keyPackageManagementUIState.keyPackageBundles.size)
                            )
                        }

                        items(
                            items = keyPackageManagementUIState.keyPackageBundles
                        ) { marmotKeyPackageBundle ->

                            Card {
                                ListItem(
                                    leadingContent = {
                                        if (marmotKeyPackageBundle.rotated) {
                                            Icon(
                                                Icons.Default.Unpublished,
                                                contentDescription = "Key package rotated"
                                            )
                                        } else if (marmotKeyPackageBundle.consumed) {
                                            Icon(
                                                Icons.Default.DoneAll,
                                                contentDescription = "Consumed key package event"
                                            )
                                        } else {
                                            // TODO: Get broadcast status of key package...
                                            Icon(
                                                Icons.Default.VerifiedUser,
                                                contentDescription = "Key package published"
                                            )
                                        }
                                    },
                                    headlineContent = {
                                        Text(
                                            text = marmotKeyPackageBundle.id,
                                            maxLines = 1,
                                            overflow = TextOverflow.MiddleEllipsis
                                        )
                                    },
                                    supportingContent = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = marmotKeyPackageBundle.createdAt.toFormattedTimeAndDateString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )

                                            Spacer(
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                    },
                                    trailingContent = {
                                        if (marmotKeyPackageBundle.rotated.not() && marmotKeyPackageBundle.consumed.not()) {
                                            IconButton(
                                                onClick = {
                                                    keyPackageManagementViewModel.rotateKeyPackage(
                                                        marmotKeyPackageBundle
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.LockReset,
                                                    contentDescription = "Rotate key"
                                                )
                                            }
                                        }

                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        KeyPackageManagementUIState.Loading -> {
            LoadingDataIndicator()
        }
        KeyPackageManagementUIState.NotFound -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(Res.string.we_couldn_t_find_the_local_profile_please))
            }
        }
    }
}

@Preview
@Composable
private fun UnannouncedProfileScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            KeyPackageManagementScreen(
                initialActiveProfileUIState = KeyPackageManagementUIState.Loaded(
                    keyPackageBundles = listOf(
                        MarmotKeyPackageBundle(
                            id = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                            publicKey = "",
                            tlsEncodedMarmotKeyPackage = ByteArray(0),
                            ncryptsecInitPrivateKey = "",
                            ncryptsecEncryptionPrivateKey = "",
                            ncryptsecSignaturePrivateKey = "",
                            consumed = true
                        )
                    )
                ),
                activeUserPublicKey = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                nostrEventId = "",
                marmotRepository = MarmotRepository.NO_OP_MARMOT_KEY_PACKAGE_BUNDLE,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}