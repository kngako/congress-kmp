package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationContributor

@Dao
interface MantraTranslationContributorDao {
    @Upsert
    suspend fun upsert(mantraTranslationContributor: MantraTranslationContributor)
}