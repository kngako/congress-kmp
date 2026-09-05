package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationChunk

@Dao
interface MantraTranslationChunkDao {
    @Upsert
    suspend fun upsert(mantraTranslationChunk: MantraTranslationChunk)

    @Query("SELECT * FROM MantraTranslationChunk WHERE translationChapterId = :translationChapterId ORDER BY `index` ASC")
    suspend fun getTranslationChunksByTranslationChapterId(translationChapterId: String): List<MantraTranslationChunk>

    @Query("DELETE FROM MantraTranslationChunk WHERE id = :id")
    suspend fun deleteById(id: String)
}