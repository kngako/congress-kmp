package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraTranslationArtifactVersion

@Dao
interface MantraTranslationArtifactVersionDao {
    @Upsert
    suspend fun upsert(mantraTranslationArtifactVersion: MantraTranslationArtifactVersion)

    /** Translations targeting any version of an artifact. */
    @Query(
        """
        SELECT MantraTranslationArtifactVersion.* FROM MantraTranslationArtifactVersion
        INNER JOIN MantraArtifactVersion
            ON MantraTranslationArtifactVersion.artifactVersionId = MantraArtifactVersion.id
        WHERE MantraArtifactVersion.artifactId = :artifactId
        ORDER BY MantraTranslationArtifactVersion.createdAt DESC
        """
    )
    suspend fun getTranslationsByArtifactId(artifactId: String): List<MantraTranslationArtifactVersion>
}