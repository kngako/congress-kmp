package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Dao
interface MarmotKeyPackageBundleDao {
    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE publicKey = :publicKey ORDER BY createdAt DESC")
    fun getAllMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle>

    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE id = :id ORDER BY createdAt DESC")
    fun getMarmotKeyPackageBundleById(id: String): MarmotKeyPackageBundle?

    @Upsert
    suspend fun upsert(marmotKeyPackageBundle: MarmotKeyPackageBundle)
}