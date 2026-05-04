package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeResult
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface NegentropySynchronizeResultDao {
    @Upsert
    fun upsert(negentropySynchronizeResult: NegentropySynchronizeResult)
}