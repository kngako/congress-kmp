package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MarmotGroupEvent

@Dao
interface MarmotGroupEventDao {

    @Upsert
    suspend fun upsert(marmotGroupEvent: MarmotGroupEvent)
}