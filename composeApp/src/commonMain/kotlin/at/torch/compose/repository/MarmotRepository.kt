package at.torch.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey

interface MarmotRepository {

    suspend fun saveMarmotKeyPackageBundle(
        publicKey: HexKey,
        nsecPassword: String
    )

    companion object {
        val NO_OP_MARMOT_KEY_PACKAGE_BUNDLE = object: MarmotRepository {
            override suspend fun saveMarmotKeyPackageBundle(
                publicKey: HexKey,
                nsecPassword: String
            ) {
                TODO("Not yet implemented")
            }

        }
    }
}