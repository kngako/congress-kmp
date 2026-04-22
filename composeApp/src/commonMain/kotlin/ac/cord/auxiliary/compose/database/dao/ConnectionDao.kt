package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalFollowers
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalFollowing
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM Connection LIMIT :limit")
    fun  getAllConnections(limit: Int = 21): List<Connection>

    @Query("SELECT * FROM Connection WHERE sourcePublicKey = :sourcePublicKey")
    fun observePublicKeyFollowing(sourcePublicKey: String): Flow<LocalFollowing?>

    @Query("SELECT * FROM Connection WHERE destinationPublicKey = :destinationPublicKey")
    fun observePublicKeyFollowers(destinationPublicKey: String): Flow<LocalFollowers?>


    @Insert
    suspend fun insertPlaceholderProfile(connection: Connection)

    @Upsert
    suspend fun upsert(connection: Connection)

}