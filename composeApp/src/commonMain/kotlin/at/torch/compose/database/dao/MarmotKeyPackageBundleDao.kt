package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackageBundle

@Dao
interface MarmotKeyPackageBundleDao {
    @Upsert
    suspend fun upsert(marmotKeyPackageBundle: MarmotKeyPackageBundle)
}