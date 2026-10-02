package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraProjectArtifactVersion

@Dao
interface MantraProjectArtifactVersionDao {
    @Upsert
    suspend fun upsert(mantraProjectArtifactVersion: MantraProjectArtifactVersion)

    @Query("SELECT * FROM MantraProjectArtifactVersion WHERE projectId = :projectId")
    suspend fun getByProjectId(projectId: String): List<MantraProjectArtifactVersion>

    @Query("SELECT * FROM MantraProjectArtifactVersion WHERE id = :id")
    suspend fun getById(id: String): MantraProjectArtifactVersion?

    /**
     * The versions themselves, resolved here rather than by the caller.
     *
     * No screen wants a join row, and resolving it in one indexed query beats an
     * N+1 in a view model.
     */
    @Query(
        """
        SELECT v.* FROM MantraArtifactVersion v
        INNER JOIN MantraProjectArtifactVersion m ON m.artifactVersionId = v.id
        WHERE m.projectId = :projectId
        ORDER BY v.versionLabel
        """
    )
    suspend fun getArtifactVersionsByProjectId(projectId: String): List<MantraArtifactVersion>

    /** The inverse: which projects a version is part of. */
    @Query(
        """
        SELECT p.* FROM MantraProject p
        INNER JOIN MantraProjectArtifactVersion m ON m.projectId = p.id
        WHERE m.artifactVersionId = :artifactVersionId
        ORDER BY p.name
        """
    )
    suspend fun getProjectsByArtifactVersionId(artifactVersionId: String): List<MantraProject>
}
