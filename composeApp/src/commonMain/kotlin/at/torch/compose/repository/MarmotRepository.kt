package at.torch.compose.repository

import at.torch.compose.database.model.MarmotKeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey

interface MarmotRepository {

    suspend fun publishMarmotKeyPackageBundle(
        publicKey: HexKey,
        nsecPassword: String
    )

    suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle>

    companion object {
        val NO_OP_MARMOT_KEY_PACKAGE_BUNDLE = object: MarmotRepository {
            override suspend fun publishMarmotKeyPackageBundle(
                publicKey: HexKey,
                nsecPassword: String
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle> {
                return emptyList()
            }

        }
    }
}