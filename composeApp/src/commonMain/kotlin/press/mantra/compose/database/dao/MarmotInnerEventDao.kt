package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MarmotInnerEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface MarmotInnerEventDao {

    @Query("SELECT * FROM MarmotInnerEvent WHERE publicKey = :publicKey AND marmotGroupEventId IS NULL")
    fun observeUnprocessedMarmotInnerEvents(publicKey: String): Flow<MarmotInnerEvent?>

    @Upsert
    suspend fun upsert(marmotInnerEvent: MarmotInnerEvent)

    @Query("DELETE FROM MarmotInnerEvent WHERE id = :id")
    suspend fun deleteById(id: String)
}