package at.torch.compose.repository

import at.torch.compose.database.model.MarmotInnerEvent
import at.torch.compose.database.model.MarmotKeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.flow.Flow

interface MarmotRepository {

    suspend fun publishMarmotKeyPackageBundle(
        publicKey: HexKey,
        nsecPassword: String
    )

    suspend fun rotateMarmotKeyPackageBundle(marmotKeyPackageBundle: MarmotKeyPackageBundle)

    suspend fun observeMarmotKeyPackageBundles(publicKey: HexKey): Flow<List<MarmotKeyPackageBundle>>

    suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle>

    suspend fun observeUnprocessedMarmotInnerEvents(publicKey: HexKey): Flow<MarmotInnerEvent?>

    suspend fun observeActiveMarmotKeyPackageBundle(publicKey: HexKey): Flow<MarmotKeyPackageBundle?>

    suspend fun encryptAndSendMarmotInnerEvent(marmotInnerEvent: MarmotInnerEvent, nostrSignerSync: NostrSignerSync)

    companion object {
        val NO_OP_MARMOT_KEY_PACKAGE_BUNDLE = object: MarmotRepository {
            override suspend fun publishMarmotKeyPackageBundle(
                publicKey: HexKey,
                nsecPassword: String
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun rotateMarmotKeyPackageBundle(marmotKeyPackageBundle: MarmotKeyPackageBundle) {
                TODO("Not yet implemented")
            }

            override suspend fun observeMarmotKeyPackageBundles(publicKey: HexKey): Flow<List<MarmotKeyPackageBundle>> {
                TODO("Not yet implemented")
            }

            override suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle> {
                return emptyList()
            }

            override suspend fun observeUnprocessedMarmotInnerEvents(publicKey: HexKey): Flow<MarmotInnerEvent?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeActiveMarmotKeyPackageBundle(publicKey: HexKey): Flow<MarmotKeyPackageBundle?> {
                TODO("Not yet implemented")
            }

            override suspend fun encryptAndSendMarmotInnerEvent(
                marmotInnerEvent: MarmotInnerEvent,
                nostrSignerSync: NostrSignerSync
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}