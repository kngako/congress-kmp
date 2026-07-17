package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.MantraTranslationChapterContributor

@Dao
interface MantraTranslationChapterContributorDao {
    @Upsert
    suspend fun upsert(mantraTranslationChapterContributor: MantraTranslationChapterContributor)
}