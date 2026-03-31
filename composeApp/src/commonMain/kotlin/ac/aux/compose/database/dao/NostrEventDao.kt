package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Upsert
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface NostrEventDao {
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) ORDER BY createdAt DESC")
    fun  observeNostrEvents(kinds: Array<Kind>): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) ORDER BY savedAt DESC")
    fun  getAllNostrEvents(kinds: Array<Kind>): List<NostrEvent>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun observeNostrEventById(id: String): Flow<LocalNostrEvent?>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun getNostrEventById(id: String): NostrEvent?

    @Upsert
    suspend fun upsert(nostrEvent: NostrEvent)

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(nostrEvent: NostrEvent)
}