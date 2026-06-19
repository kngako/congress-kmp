package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.MarmotKeyPackageBundle
import at.torch.compose.database.model.Mention

@Dao
interface MarmotKeyPackageBundleDao {
    @Upsert
    suspend fun upsert(marmotKeyPackageBundle: MarmotKeyPackageBundle)
}