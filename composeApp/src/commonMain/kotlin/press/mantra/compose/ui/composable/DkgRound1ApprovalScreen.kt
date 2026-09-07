package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.StateFlow
import press.mantra.compose.ui.theme.spacing

/**
 * Asks whether to contribute to the key itself.
 *
 * The step where the member's own secret material first shapes the outcome.
 * Joining settled who is in the ceremony; this settles what the key is, and once
 * it is out there is no version of the key that does not include it.
 *
 * The participant set is worth showing here rather than a count, because this is
 * the last moment at which "who am I making a key with" is still a question the
 * member can act on — everyone is now known, and the ceremony has not yet
 * committed to anything.
 */
@Composable
fun DkgRound1ApprovalScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    chatRepository: ChatRepository,
    dkgRepository: DkgRepository,
    onDone: () -> Unit,
) {
    DkgApprovalScaffold(
        step = DkgApprovalStep.ROUND_1,
        title = "Contribute to the shared key?",
        approveLabel = "Contribute",
        activeUserPublicKey = activeUserPublicKey,
        chatRoomId = chatRoomId,
        activeWalletStateFlow = activeWalletStateFlow,
        chatRepository = chatRepository,
        dkgRepository = dkgRepository,
        onDone = onDone
    ) { state ->
        val session = state.session

        Text(
            text = "Everyone has joined. ${state.participantCount} members are making a key that " +
                "takes ${session?.threshold ?: 0} of them to sign with.",
            style = MaterialTheme.typography.bodyLarge
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.itemGap)
        ) {
            DkgApprovalPoint(
                "Your device publishes its contribution to the key. Every member's is mixed in, " +
                    "so no single device ever holds the whole key."
            )
            DkgApprovalPoint("Your share of it never leaves this device.")
            DkgApprovalPoint("You will be asked once more, to check the combined result before it is final.")
        }

        Text(
            text = "Taking part with these members",
            style = MaterialTheme.typography.titleSmall
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.relatedGap)
        ) {
            state.ritualMembers.forEach { member ->
                val key = member.participant.participantPublicKey

                Text(
                    text = if (key == activeUserPublicKey) {
                        "You"
                    } else {
                        member.profile?.humanReadableNameOrPubkey() ?: key.take(12)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
