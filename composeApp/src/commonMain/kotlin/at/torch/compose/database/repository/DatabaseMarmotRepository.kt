package at.torch.compose.database.repository

import at.torch.compose.database.TorchDatabase
import at.torch.compose.database.model.MarmotKeyPackageBundle
import at.torch.compose.database.model.UnsignedNostrEvent
import at.torch.compose.exceptions.MarmotMissingChatGroupException
import at.torch.compose.extensions.exporterSecret
import at.torch.compose.extensions.toHex
import at.torch.compose.nostr.Relays
import at.torch.compose.repository.MarmotRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager.Companion.KEY_PACKAGE_LIFETIME_SECONDS
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.CommitOrdering
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.DecryptedMessage
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.RetainedEpochSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource
import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * Based on MlsGroupManager in quarts...
 */
class DatabaseMarmotRepository(
    val database: TorchDatabase,
    val scope: CoroutineScope
): MarmotRepository {
    private val logger = Logger.withTag(TAG)

    override suspend fun publishMarmotKeyPackageBundle(
        publicKey: HexKey,
        nsecPassword: String
    ) {
        logger.d("saveMarmotKeyPackageBundle: $publicKey")
        // KeyPackage...
        val dTag = KeyPackageUtils.generateRandomDTag() // TODO: Persist this...
        val identity = publicKey.hexToByteArray()
        val bundle = generateKeyPackage(identity, dTag)

        val keyPackageBytes = bundle.keyPackage.toTlsBytes()
        val keyPackageBase64 = Base64.encode(keyPackageBytes)
        val keyPackageRef = bundle.keyPackage.reference().toHexKey()

        val keyPackageTemplate = KeyPackageEvent.build(
            keyPackageBase64 = keyPackageBase64,
            dTagSlot = dTag,
            keyPackageRef = keyPackageRef,
            relays = Relays.DefaultDMRelayList,
        )

        database.unsignedNostrEventDao().upsert(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = keyPackageTemplate.kind,
                tags = keyPackageTemplate.tags,
                createdAt = Instant.fromEpochSeconds(keyPackageTemplate.createdAt),
                content = keyPackageTemplate.content
            )
        )

        val id = EventHasher.hashId(
            pubKey = publicKey,
            createdAt = keyPackageTemplate.createdAt,
            kind = keyPackageTemplate.kind,
            tags = keyPackageTemplate.tags,
            content = keyPackageTemplate.content
        )
        logger.d("keyPackageEventId: $id")

        database.marmotKeyPackageBundleDao().upsert(
            MarmotKeyPackageBundle(
                id = id,
                publicKey = publicKey,
                tlsEncodedMarmotKeyPackage = bundle.keyPackage.toTlsBytes(),
                ncryptsecInitPrivateKey = bundle.initPrivateKey.toHex(), // TODO: ncryptSec this using the private key
                ncryptsecEncryptionPrivateKey = bundle.encryptionPrivateKey.toHex(), // TODO: ncryptSec this using the private key
                ncryptsecSignaturePrivateKey = bundle.signaturePrivateKey.toHex() // TODO: ncryptSec this using the private key
            )
        )
    }

    override suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle> {
        return database.marmotKeyPackageBundleDao().getAllMarmotKeyPackageBundles(publicKey)
    }

    private fun generateKeyPackage(
        identity: ByteArray,
        dTagSlot: String = KeyPackageUtils.PRIMARY_SLOT,
    ): KeyPackageBundle {
        val initKp = X25519.generateKeyPair()
        val encKp = X25519.generateKeyPair()
        val sigKp = Ed25519.generateKeyPair()

        val leafNode =
            buildKeyPackageLeafNode(
                encryptionKey = encKp.publicKey,
                signatureKey = sigKp.publicKey,
                identity = identity,
                signingKey = sigKp.privateKey,
            )

        val unsigned =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                // LastResort (0x000A) marks this as a last-resort KeyPackage per MLS Extensions draft.
                // MDK always marks KeyPackages as last-resort via .mark_as_last_resort().
                extensions = listOf(
                    Extension(
                        extensionType = 0x000A,
                        extensionData = ByteArray(0)
                    )
                ),
                signature = ByteArray(0),
            )
        val keyPackage =
            unsigned.copy(
                signature =
                    MlsCryptoProvider.signWithLabel(
                        sigKp.privateKey,
                        "KeyPackageTBS",
                        unsigned.encodeTbs(),
                    ),
            )

        val bundle = KeyPackageBundle(
            keyPackage,
            initPrivateKey = initKp.privateKey,
            encryptionPrivateKey = encKp.privateKey,
            signaturePrivateKey = sigKp.privateKey
        )

        return bundle
    }


    private fun buildKeyPackageLeafNode(
        encryptionKey: ByteArray,
        signatureKey: ByteArray,
        identity: ByteArray,
        signingKey: ByteArray,
    ): LeafNode {
        val now = TimeUtils.now()
        val unsigned =
            LeafNode(
                encryptionKey = encryptionKey,
                signatureKey = signatureKey,
                credential = Credential.Basic(identity),
                capabilities =
                    Capabilities(
                        extensions =
                            listOf(
                                0x000A, // LastResort (required by OpenMLS validation)
                                0xF2EE, // NostrGroupData (required by group's RequiredCapabilities)
                            ),
                        proposals =
                            listOf(
                                0x000A, // SelfRemove (required by group's RequiredCapabilities)
                            ),
                    ),
                leafNodeSource = LeafNodeSource.KEY_PACKAGE,
                lifetime = Lifetime(
                    notBefore = now,
                    notAfter = now + KEY_PACKAGE_LIFETIME_SECONDS
                ),
                extensions = emptyList(),
                signature = ByteArray(0),
            )

        val tbs = unsigned.encodeTbs(groupId = null, leafIndex = null)
        val signature = MlsCryptoProvider.signWithLabel(signingKey, "LeafNodeTBS", tbs)
        return unsigned.copy(signature = signature)
    }

    companion object {
        private const val TAG = "DatabaseMarmotRepository"
    }
}