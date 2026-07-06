package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import at.torch.compose.database.model.MarmotRetainedEpochSecret

@Dao
interface MarmotRetainedEpochSecretDao {
    @Query("SELECT * FROM MarmotRetainedEpochSecret WHERE epoch < :epochCutOffPoint")
    suspend fun defenestratableMarmotRetainedEpochSecret(epochCutOffPoint: Long): List<MarmotRetainedEpochSecret>

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(marmotRetainedEpochSecret: MarmotRetainedEpochSecret)

    @Delete
    suspend fun defenestrate(marmotRetainedEpochSecrets: List<MarmotRetainedEpochSecret>)

}