package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.bitcoin.PublicKey

@Dao
interface MarmotKeyPackageDao {
    @Query("SELECT * FROM MarmotKeyPackage WHERE publicKey = :publicKey ORDER BY createdAt DESC")
    suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage?

    @Query("SELECT * FROM MarmotKeyPackage WHERE id = :id ORDER BY createdAt DESC")
    suspend fun getMarmotKeyPackageById(id: HexKey): MarmotKeyPackage?

    @Upsert
    suspend fun upsert(marmotKeyPackage: MarmotKeyPackage)
}