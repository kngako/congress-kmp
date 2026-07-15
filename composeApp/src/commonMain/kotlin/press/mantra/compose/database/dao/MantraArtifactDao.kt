package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraArtifact

@Dao
interface MantraArtifactDao {
    @Upsert
    suspend fun upsert(mantraArtifact: MantraArtifact)
}