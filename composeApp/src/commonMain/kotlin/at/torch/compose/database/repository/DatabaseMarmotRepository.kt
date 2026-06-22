package at.torch.compose.database.repository

import at.torch.compose.database.TorchDatabase
import at.torch.compose.database.model.MarmotKeyPackageBundle
import at.torch.compose.extensions.toHex
import at.torch.compose.repository.MarmotRepository
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.CoroutineScope

class DatabaseMarmotRepository(
    val database: TorchDatabase,
    val scope: CoroutineScope
): MarmotRepository {
    override suspend fun saveMarmotKeyPackageBundle(
        publicKey: HexKey,
        keyPackageBundle: KeyPackageBundle,
        nsecPassword: String
    ) {
        val tlsWriter = TlsWriter()

        keyPackageBundle.keyPackage.encodeTls(tlsWriter)

        val nip49 = Nip49()

        database.marmotKeyPackageBundleDao().upsert(
            MarmotKeyPackageBundle(
                publicKey = publicKey,
                tlsEncodedMarmotKeyPackage = tlsWriter.toByteArray().toHex(),
                ncryptsecInitPrivateKey = keyPackageBundle.initPrivateKey.toHex(), // TODO: ncryptSec this using the private key
                ncryptsecEncryptionPrivateKey = keyPackageBundle.encryptionPrivateKey.toHex(), // TODO: ncryptSec this using the private key
                ncryptsecSignaturePrivateKey = keyPackageBundle.signaturePrivateKey.toHex() // TODO: ncryptSec this using the private key
            )
        )

    }
}