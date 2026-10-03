package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraArtifact

@Dao
interface MantraArtifactDao {
    @Upsert
    suspend fun upsert(mantraArtifact: MantraArtifact)

    @Query("SELECT * FROM MantraArtifact WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    suspend fun getArtifactsByChatRoomId(chatRoomId: String): List<MantraArtifact>

    @Query("SELECT * FROM MantraArtifact WHERE id = :id")
    suspend fun getArtifactById(id: String): MantraArtifact?

    /**
     * The artifacts written in [dialectId], newest first.
     *
     * Scoped by the dialect alone and not also by a room. `dialectId` is a
     * foreign key into this room's own rows, so it cannot reach another room's
     * artifacts and a second `chatRoomId` filter would be a second spelling of a
     * constraint the key already enforces. The day a dialect can be named across
     * rooms -- see Phase 9 of docs/project-feature.md -- this is one of the
     * places that has to learn about it.
     */
    @Query("SELECT * FROM MantraArtifact WHERE dialectId = :dialectId ORDER BY createdAt DESC")
    suspend fun getArtifactsByDialectId(dialectId: String): List<MantraArtifact>
}