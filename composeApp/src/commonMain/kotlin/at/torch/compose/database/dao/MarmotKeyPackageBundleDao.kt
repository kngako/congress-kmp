package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackageBundle

@Dao
interface MarmotKeyPackageBundleDao {
    @Query("SELECT * FROM MarmotKeyPackageBundle WHERE id = :id ORDER BY createdAt DESC")
    fun getMarmotKeyPackageBundleById(id: String): MarmotKeyPackageBundle?

    @Upsert
    suspend fun upsert(marmotKeyPackageBundle: MarmotKeyPackageBundle)
}