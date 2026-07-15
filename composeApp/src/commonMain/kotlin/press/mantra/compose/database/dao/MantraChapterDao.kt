package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraChapter

@Dao
interface MantraChapterDao {
    @Upsert
    suspend fun upsert(mantraChapter: MantraChapter)
}