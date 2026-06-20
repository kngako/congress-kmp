package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotKeyPackage

@Dao
interface MarmotKeyPackageDao {

    @Upsert
    suspend fun upsert(marmotKeyPackage: MarmotKeyPackage)
}