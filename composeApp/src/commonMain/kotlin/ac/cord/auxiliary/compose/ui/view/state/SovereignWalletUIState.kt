package ac.cord.auxiliary.compose.ui.view.state

import fr.acinq.phoenix.PhoenixBusiness

sealed interface SovereignWalletUIState {
    data object Uninitialized: SovereignWalletUIState

    data class Success(
        val publicKey: String,
        val balanceInSatoshis: Long,
    ): SovereignWalletUIState


    data object Error: SovereignWalletUIState
    data object Loading: SovereignWalletUIState
}

