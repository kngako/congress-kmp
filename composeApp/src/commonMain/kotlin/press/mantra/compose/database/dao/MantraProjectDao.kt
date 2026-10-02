package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MantraProject

@Dao
interface MantraProjectDao {
    @Upsert
    suspend fun upsert(mantraProject: MantraProject)

    @Query("SELECT * FROM MantraProject WHERE chatRoomId = :chatRoomId ORDER BY name")
    suspend fun getProjectsByChatRoomId(chatRoomId: String): List<MantraProject>

    @Query("SELECT * FROM MantraProject WHERE id = :id")
    suspend fun getProjectById(id: String): MantraProject?
}
