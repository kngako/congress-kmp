package press.mantra.compose.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.StateFlow
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.view.model.RecoveryPhraseViewModel
import press.mantra.compose.ui.view.state.RecoveryPhraseUIState

private const val PHRASE_INSTRUCTIONS =
    "The recovery phrase (sometimes called a seed) is a list of 12 English words. It is the only " +
            "way back to this profile: the key that signs as you, and the wallet that holds your " +
            "coins, are both derived from it.\n\n" +
            "Only you have this phrase. Keep it private — nobody from mantra will ever ask you " +
            "for it.\n\n" +
            "Do not lose it. Write it down and keep it somewhere safe that is not this phone. If " +
            "you lose both the phone and the phrase, this profile and its funds are gone for good."

private const val PHRASE_DERIVATION =
    "BIP39 seed with the standard BIP84 derivation path. The profile's nostr key comes off the " +
            "same seed, so these 12 words restore both."

/**
 * The twelve words behind this profile, shown once and on request.
 *
 * The reveal is deliberately a separate step from opening the screen: reading
 * the seed goes to the keystore, and nobody should walk past a phone that is
 * quietly displaying it.
 */
@Composable
fun RecoveryPhraseScreen(
    phoenixGlobal: PhoenixGlobal,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    onNavigateBack: () -> Unit,
) {
    val recoveryPhraseViewModel: RecoveryPhraseViewModel = viewModel(
        factory = RecoveryPhraseViewModel.factory(
            phoenixGlobal = phoenixGlobal,
            activeWalletStateFlow = activeWalletStateFlow
        )
    )

    val recoveryPhraseUIState by recoveryPhraseViewModel.recoveryPhraseUIState.collectAsState()
    val isBackupDone by recoveryPhraseViewModel.isBackupDone.collectAsState()
    val isDisclaimerRead by recoveryPhraseViewModel.isDisclaimerRead.collectAsState()
    val showBackupNotice by recoveryPhraseViewModel.showBackupNotice.collectAsState()

    RecoveryPhraseContent(
        recoveryPhraseUIState = recoveryPhraseUIState,
        isBackupDone = isBackupDone,
        isDisclaimerRead = isDisclaimerRead,
        showBackupNotice = showBackupNotice,
        onRevealRecoveryPhrase = recoveryPhraseViewModel::revealRecoveryPhrase,
        onHideRecoveryPhrase = recoveryPhraseViewModel::hideRecoveryPhrase,
        onBackupDoneChange = recoveryPhraseViewModel::setBackupDone,
        onDisclaimerReadChange = recoveryPhraseViewModel::setDisclaimerRead,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * The screen once the state and the two backup preferences have been read.
 * Everything the view model owns arrives as a value or a callback, so a preview
 * can render any branch without a seed on disk.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryPhraseContent(
    recoveryPhraseUIState: RecoveryPhraseUIState,
    isBackupDone: Boolean?,
    isDisclaimerRead: Boolean?,
    showBackupNotice: Boolean,
    onRevealRecoveryPhrase: () -> Unit,
    onHideRecoveryPhrase: () -> Unit,
    onBackupDoneChange: (Boolean) -> Unit,
    onDisclaimerReadChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recovery Phrase"
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
                Text(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    text = PHRASE_INSTRUCTIONS,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (showBackupNotice) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null
                            )

                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )

                            Column {
                                Text(
                                    text = "You have not backed up your recovery phrase",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text(
                                    text = "Lose this phone before you do, and the profile goes " +
                                            "with it.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (recoveryPhraseUIState) {
                            is RecoveryPhraseUIState.Hidden -> {
                                Button(
                                    onClick = onRevealRecoveryPhrase
                                ) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = "Display recovery phrase"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(10.dp)
                                    )

                                    Text(
                                        text = "Display recovery phrase"
                                    )
                                }
                            }

                            is RecoveryPhraseUIState.Revealing -> {
                                LoadingDataIndicator(
                                    fillScreen = false,
                                    text = "Unlocking your phrase…"
                                )
                            }

                            is RecoveryPhraseUIState.Revealed -> {
                                RecoveryPhraseWords(
                                    words = recoveryPhraseUIState.words
                                )

                                Spacer(
                                    modifier = Modifier.height(10.dp)
                                )

                                TextButton(
                                    onClick = onHideRecoveryPhrase
                                ) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = "Hide recovery phrase"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(10.dp)
                                    )

                                    Text(
                                        text = "Hide"
                                    )
                                }
                            }

                            is RecoveryPhraseUIState.Error -> {
                                Text(
                                    text = when (recoveryPhraseUIState) {
                                        is RecoveryPhraseUIState.Error.NoActiveWallet ->
                                            "No wallet is open on this device, so there is no " +
                                                    "phrase to show."
                                        is RecoveryPhraseUIState.Error.NoPhraseForThisWallet ->
                                            "This device holds no phrase for the profile that is " +
                                                    "signed in."
                                        is RecoveryPhraseUIState.Error.SeedUnreadable ->
                                            "Could not unlock your phrase. Please try again."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(
                                    modifier = Modifier.height(10.dp)
                                )

                                TextButton(
                                    onClick = onRevealRecoveryPhrase
                                ) {
                                    Text(
                                        text = "Try again"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Backup confirmation",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBackupDone == null || isDisclaimerRead == null) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            text = "Loading preferences…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        RecoveryPhraseCheckbox(
                            checked = isBackupDone,
                            text = "I have saved my recovery phrase somewhere safe.",
                            onCheckedChange = onBackupDoneChange
                        )

                        RecoveryPhraseCheckbox(
                            checked = isDisclaimerRead,
                            text = "I understand that if I lose this phone and my recovery " +
                                    "phrase, I lose this profile and the funds in its wallet.",
                            onCheckedChange = onDisclaimerReadChange
                        )
                    }
                }
            }

            item {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    text = PHRASE_DERIVATION,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The words themselves, numbered and in two columns so they can be checked off
 * against what the user writes down.
 */
@Composable
private fun RecoveryPhraseWords(words: List<String>) {
    Text(
        text = "KEEP THIS PHRASE SAFE.\nDO NOT SHARE IT.",
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center
    )

    Spacer(
        modifier = Modifier.height(16.dp)
    )

    // Read down the left column and then the right, the way the words are written on a card:
    // #1..#6 beside #7..#12 rather than odds beside evens.
    val pairedWords = remember(words) {
        val half = (words.size + 1) / 2
        List(half) { index ->
            words[index] to words.getOrNull(index + half)
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        pairedWords.forEachIndexed { index, (first, second) ->
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                RecoveryPhraseWord(
                    position = index + 1,
                    word = first,
                    modifier = Modifier.weight(1f)
                )

                RecoveryPhraseWord(
                    position = index + pairedWords.size + 1,
                    word = second,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RecoveryPhraseWord(
    position: Int,
    word: String?,
    modifier: Modifier = Modifier,
) {
    if (word == null) {
        Spacer(modifier = modifier)
        return
    }

    Row(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.width(28.dp),
            text = "#$position",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.width(6.dp)
        )

        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecoveryPhraseCheckbox(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview
@Composable
private fun RecoveryPhraseScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            RecoveryPhraseContent(
                recoveryPhraseUIState = RecoveryPhraseUIState.Revealed(
                    words = listOf(
                        "abandon", "ability", "able", "about", "above", "absent",
                        "absorb", "abstract", "absurd", "abuse", "access", "accident"
                    )
                ),
                isBackupDone = false,
                isDisclaimerRead = true,
                showBackupNotice = true,
                onRevealRecoveryPhrase = {},
                onHideRecoveryPhrase = {},
                onBackupDoneChange = {},
                onDisclaimerReadChange = {},
                onNavigateBack = {},
            )
        }
    }
}

@Preview
@Composable
private fun RecoveryPhraseScreenHiddenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            RecoveryPhraseContent(
                recoveryPhraseUIState = RecoveryPhraseUIState.Hidden,
                isBackupDone = false,
                isDisclaimerRead = false,
                showBackupNotice = true,
                onRevealRecoveryPhrase = {},
                onHideRecoveryPhrase = {},
                onBackupDoneChange = {},
                onDisclaimerReadChange = {},
                onNavigateBack = {},
            )
        }
    }
}
