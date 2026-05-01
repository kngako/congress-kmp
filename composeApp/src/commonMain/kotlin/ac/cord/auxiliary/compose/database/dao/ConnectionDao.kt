package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Connection
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM Connection LIMIT :limit")
    fun  getAllConnections(limit: Int = 21): List<Connection>


    @Query("SELECT * FROM Connection WHERE (sourcePublicKey = :alicePublicKey AND destinationPublicKey = :bobPublicKey) OR (sourcePublicKey = :bobPublicKey AND destinationPublicKey = :alicePublicKey)")
    fun  observeRelation(alicePublicKey: HexKey, bobPublicKey: HexKey): Flow<List<Connection>>

    @Insert
    suspend fun insertPlaceholderProfile(connection: Connection)

    @Upsert
    suspend fun upsert(connection: Connection)

    @Delete
    suspend fun delete(connection: Connection)

}