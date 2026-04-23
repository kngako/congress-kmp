package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Connection
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM Connection LIMIT :limit")
    fun  getAllConnections(limit: Int = 21): List<Connection>


    @Insert
    suspend fun insertPlaceholderProfile(connection: Connection)

    @Upsert
    suspend fun upsert(connection: Connection)

}