package at.torch.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.extensions.toHex
import at.torch.compose.nostr.Relays
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.view.state.ChatRoomDetailUIState
import at.torch.compose.ui.view.state.form.ChatRoomCreationFormState
import at.torch.compose.ui.view.state.form.CreateProfileFormState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519KeyPair
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.RandomInstance
import fr.acinq.bitcoin.Crypto
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatRoomCreationViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val chatRoomCreationFormState: ChatRoomCreationFormState = ChatRoomCreationFormState(),
): ViewModel() {

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)


    fun createChatRoom(
        onNavigateToChatRoom: (Route) -> Unit
    ) {
        // TODO: Load keypair...
        activeWalletStateFlow.value?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()?.let { nostrPrivateKey ->
            val keyPair = KeyPair(
                privKey = nostrPrivateKey.value.toByteArray()
            )

            val name = chatRoomCreationFormState.nameField.textFieldState.text
            val description = chatRoomCreationFormState.descriptionField.textFieldState.text

            val gid = RandomInstance.bytes(32).toHexKey() // TODO: Generate GID through frost...

            // Stamp initial metadata via the shared factory so UI + CLI stay
            // byte-identical. Bake the MarmotGroupData extension into the
            // epoch-0 GroupContext directly (see `MarmotManager.createGroup`)
            // so later invitees receive a pre-populated group from the
            // welcome and never have to chase an undecryptable bootstrap
            // commit that predates their membership.
            val metadata = MarmotGroupData.bootstrap(
                nostrGroupId = gid,
                creatorPubKey = keyPair.pubKey.toHexKey(),
                outboxRelays = Relays.DefaultDMRelayList.map { it.url },
                name = name.toString(),
                description = description.toString()
            )

            val extras = listOf(metadata.toExtension())

            nostrPrivateKey.value.toByteArray().let { privateKey ->
                val signingKeyPair = Ed25519.generateKeyPair()
//              TODO: Use nostrPrivateKey  Ed25519.publicFromPrivate(privateKey).let { ed25519pubKey ->
//                    Ed25519KeyPair(privateKey, ed25519pubKey)
//                }
                val group = MlsGroup.create(keyPair.pubKey, signingKeyPair.privateKey, extras)

                viewModelScope.launch(Dispatchers.IO) {
                    val localChatRoom = chatRepository.getOrCreateChatRoom(
                        chatRoomId = group.groupId.toHex(),
                        activeUserPublicKey = keyPair.pubKey.toHexKey(),
                        relayHint = null,
                        defaultSubject = null,
                        mlsGroupState = group.saveState().encodeTls().toHex()
                    )

                    if (localChatRoom == null) {
                        onNavigateToChatRoom.invoke(
                            ImplementationPendingRoute("Something went wrong")
                        )
                    } else {
                        onNavigateToChatRoom.invoke(
                            ImplementationPendingRoute("Chat Room view ${localChatRoom.chatRoom.id}")
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ChatRoomCreationViewModel"

        fun factory(
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomCreationViewModel(
                    activeWalletStateFlow = activeWalletStateFlow,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}