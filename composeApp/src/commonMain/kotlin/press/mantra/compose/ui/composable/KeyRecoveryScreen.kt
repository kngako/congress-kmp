package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import press.mantra.compose.extensions.hexToNpubHrp
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.RecoveryPhraseRoute
import press.mantra.compose.ui.composable.navigation.routes.Route

/**
 * Everything that can put this profile back on another phone.
 *
 * A mantra profile is its key: the npub that signs, and the wallet that holds
 * coins, both come out of one seed that never leaves the device. There is no
 * account to reset, so this screen is the only thing standing between a lost
 * phone and a lost identity -- it exists to get the user to take a backup while
 * they still can.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KeyRecoveryScreen(
    activeUserPublicKey: HexKey,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (Route) -> Unit,
) {
    val activeWallet by activeWalletStateFlow.collectAsState()

    // The same preference the recovery phrase screen writes, so the state of the backup is
    // visible before the user goes looking for it.
    val showBackupNotice by remember(activeWallet) {
        activeWallet?.internalPrefs?.showSeedBackupNotice ?: flowOf(false)
    }.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Key Recovery"
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "These are your keys. Keep them safe so they can keep unlocking " +
                                "this profile and its coins, even when you lose or change your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text = activeUserPublicKey.hexToNpubHrp(),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                KeyRecoveryOption(
                    icon = Icons.Default.Spellcheck,
                    title = "Recovery Phrase",
                    description = "Write down and secure the 12 word phrase that this profile and " +
                            "its wallet are derived from.",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    status = if (showBackupNotice) {
                        KeyRecoveryStatus(
                            icon = Icons.Default.Warning,
                            text = "Not backed up yet"
                        )
                    } else {
                        KeyRecoveryStatus(
                            icon = Icons.Default.CheckCircle,
                            text = "You said you wrote it down"
                        )
                    },
                    onClick = {
                        onNavigateToRoute.invoke(RecoveryPhraseRoute)
                    }
                )
            }

            item {
                KeyRecoveryOption(
                    icon = Icons.Default.AddToDrive,
                    title = "Cloud Backup",
                    description = "Encrypt and back your recovery information up to your Google " +
                            "Drive or iCloud.",
                    onClick = {
                        onNavigateToRoute.invoke(
                            ImplementationPendingRoute("Cloud Backup")
                        )
                    }
                )
            }

            item {
                KeyRecoveryOption(
                    icon = Icons.Default.LocalHospital,
                    title = "Emergency Kit",
                    description = "Download and securely store everything needed to recover this " +
                            "profile and the coins it holds.",
                    onClick = {
                        onNavigateToRoute.invoke(
                            ImplementationPendingRoute("Emergency Kit")
                        )
                    }
                )
            }

            item {
                KeyRecoveryOption(
                    icon = Icons.Default.VolunteerActivism,
                    title = "YOLO",
                    description = "You only live once. Lose this phone and the profile goes with " +
                            "it, along with anything it holds.",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        onNavigateBack.invoke()
                    }
                )
            }

            item {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )
            }
        }
    }
}

/** A one-line verdict on an option, shown under it. */
private data class KeyRecoveryStatus(
    val icon: ImageVector,
    val text: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyRecoveryOption(
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    status: KeyRecoveryStatus? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = title
                )

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )

            status?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        it.icon,
                        contentDescription = null,
                        modifier = Modifier.width(16.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text = it.text,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun KeyRecoveryScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            KeyRecoveryScreen(
                activeUserPublicKey = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                activeWalletStateFlow = MutableStateFlow(null),
                onNavigateBack = {},
                onNavigateToRoute = {}
            )
        }
    }
}
