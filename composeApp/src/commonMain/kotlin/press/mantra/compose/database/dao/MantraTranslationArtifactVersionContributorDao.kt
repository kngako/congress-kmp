package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationArtifactVersionContributor

@Dao
interface MantraTranslationArtifactVersionContributorDao {
    @Upsert
    suspend fun upsert(mantraTranslationArtifactVersionContributor: MantraTranslationArtifactVersionContributor)
}