package press.mantra.compose.repository

import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotKeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MarmotRepository {

    suspend fun publishMarmotKeyPackageBundle(
        publicKey: HexKey,
        nsecPassword: String
    )

    suspend fun rotateMarmotKeyPackageBundle(marmotKeyPackageBundle: MarmotKeyPackageBundle)

    suspend fun observeMarmotKeyPackageBundles(publicKey: HexKey): Flow<List<MarmotKeyPackageBundle>>

    suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle>

    /**
     * The whole outbound backlog for [publicKey], oldest first.
     *
     * See `MarmotInnerEventDao.observeUnprocessedMarmotInnerEvents` for why this is a
     * list: a row that cannot be sent used to be the entire queue. Callers work
     * through it in order, and a row that fails costs only itself.
     */
    suspend fun observeUnprocessedMarmotInnerEvents(publicKey: HexKey): Flow<List<MarmotInnerEvent>>

    suspend fun observeActiveMarmotKeyPackageBundle(publicKey: HexKey): Flow<MarmotKeyPackageBundle?>

    suspend fun encryptAndSendMarmotInnerEvent(marmotInnerEvent: MarmotInnerEvent, nostrSignerSync: NostrSignerSync)

    companion object {
        /**
         * A repository with no key packages behind it: inert, and no member throws. See
         * [NostrRepository.NO_OP_NOSTR_REPOSITORY] for why a `TODO()` here is a silent
         * misattribution rather than a loud failure, and for the rules a new member follows.
         */
        val NO_OP_MARMOT_KEY_PACKAGE_BUNDLE: MarmotRepository = object : MarmotRepository {
            override suspend fun publishMarmotKeyPackageBundle(
                publicKey: HexKey,
                nsecPassword: String
            ) = Unit

            override suspend fun rotateMarmotKeyPackageBundle(marmotKeyPackageBundle: MarmotKeyPackageBundle) = Unit

            override suspend fun observeMarmotKeyPackageBundles(publicKey: HexKey): Flow<List<MarmotKeyPackageBundle>> =
                flowOf(emptyList())

            override suspend fun getMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle> =
                emptyList()

            override suspend fun observeUnprocessedMarmotInnerEvents(publicKey: HexKey): Flow<List<MarmotInnerEvent>> =
                flowOf(emptyList())

            override suspend fun observeActiveMarmotKeyPackageBundle(publicKey: HexKey): Flow<MarmotKeyPackageBundle?> =
                flowOf(null)

            override suspend fun encryptAndSendMarmotInnerEvent(
                marmotInnerEvent: MarmotInnerEvent,
                nostrSignerSync: NostrSignerSync
            ) = Unit
        }
    }
}
