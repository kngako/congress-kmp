package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MarmotKeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

@Dao
interface MarmotKeyPackageBundleDao {
    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE publicKey = :publicKey AND consumed = false AND rotated = false")
    fun observeActiveMarmotKeyPackageBundle(publicKey: String): Flow<MarmotKeyPackageBundle?>

    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE publicKey = :publicKey ORDER BY createdAt DESC")
    fun observeAllMarmotKeyPackageBundles(publicKey: HexKey): Flow<List<MarmotKeyPackageBundle>>

    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE publicKey = :publicKey ORDER BY createdAt DESC")
    fun getAllMarmotKeyPackageBundles(publicKey: HexKey): List<MarmotKeyPackageBundle>

    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE id = :id ORDER BY createdAt DESC")
    fun getMarmotKeyPackageBundleById(id: String): MarmotKeyPackageBundle?

    @Upsert
    suspend fun upsert(marmotKeyPackageBundle: MarmotKeyPackageBundle)
}