package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraChunk

@Dao
interface MantraChunkDao {
    @Upsert
    suspend fun upsert(mantraChunk: MantraChunk)

    @Query("SELECT * FROM MantraChunk WHERE chapterId = :chapterId ORDER BY `index` ASC")
    suspend fun getChunksByChapterId(chapterId: String): List<MantraChunk>
}