package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraProjectTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationArtifactVersion

@Dao
interface MantraProjectTranslationArtifactVersionDao {
    @Upsert
    suspend fun upsert(
        mantraProjectTranslationArtifactVersion: MantraProjectTranslationArtifactVersion
    )

    @Query("SELECT * FROM MantraProjectTranslationArtifactVersion WHERE projectId = :projectId")
    suspend fun getByProjectId(projectId: String): List<MantraProjectTranslationArtifactVersion>

    @Query("SELECT * FROM MantraProjectTranslationArtifactVersion WHERE id = :id")
    suspend fun getById(id: String): MantraProjectTranslationArtifactVersion?

    /** The translations themselves. See the sibling DAO's note. */
    @Query(
        """
        SELECT t.* FROM MantraTranslationArtifactVersion t
        INNER JOIN MantraProjectTranslationArtifactVersion m
            ON m.translationArtifactVersionId = t.id
        WHERE m.projectId = :projectId
        ORDER BY t.name
        """
    )
    suspend fun getTranslationArtifactVersionsByProjectId(
        projectId: String
    ): List<MantraTranslationArtifactVersion>

    /** The inverse: which projects a translation is part of. */
    @Query(
        """
        SELECT p.* FROM MantraProject p
        INNER JOIN MantraProjectTranslationArtifactVersion m ON m.projectId = p.id
        WHERE m.translationArtifactVersionId = :translationArtifactVersionId
        ORDER BY p.name
        """
    )
    suspend fun getProjectsByTranslationArtifactVersionId(
        translationArtifactVersionId: String
    ): List<MantraProject>
}
