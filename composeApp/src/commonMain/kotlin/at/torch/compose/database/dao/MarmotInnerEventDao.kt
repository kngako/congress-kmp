package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotInnerEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface MarmotInnerEventDao {

    @Upsert
    suspend fun upsert(marmotInnerEvent: MarmotInnerEvent)
}