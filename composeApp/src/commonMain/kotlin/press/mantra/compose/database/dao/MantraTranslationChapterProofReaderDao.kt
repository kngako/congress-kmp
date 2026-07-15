package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationChapterProofReader

@Dao
interface MantraTranslationChapterProofReaderDao {
    @Upsert
    suspend fun upsert(mantraTranslationChapterProofReader: MantraTranslationChapterProofReader)
}