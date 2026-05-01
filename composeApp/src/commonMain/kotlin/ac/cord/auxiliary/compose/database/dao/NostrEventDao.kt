package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import fr.acinq.bitcoin.PublicKey
import kotlinx.coroutines.flow.Flow

@Dao
interface NostrEventDao {
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND savedAt < strftime('%s', 'now')  ORDER BY createdAt DESC")
    fun  observeNostrEvents(kinds: Array<Kind>): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE content LIKE '%' || :search || '%' AND kind in (:kinds) AND savedAt < strftime('%s', 'now') ORDER BY createdAt DESC")
    fun  observeFilteredNostrEvents(
        kinds: Array<Kind>,
        search: String
    ): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND id in (:ids) AND savedAt < strftime('%s', 'now') ORDER BY createdAt DESC")
    fun  observeFilteredNostrEvents(
        kinds: Array<Kind>,
        ids: Array<HexKey>,
    ): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND pubKey in (:authors) AND savedAt < strftime('%s', 'now') ORDER BY createdAt DESC LIMIT 50")
    fun  observeAuthoredNostrEvents(
        kinds: Array<Kind>,
        authors: Array<HexKey>,
    ): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE tags LIKE '%' || :publicKey || '%' AND kind in (:kinds) AND savedAt < strftime('%s', 'now') ORDER BY createdAt DESC")
    fun  observePublicKeyMentionedNostrEvents(
        kinds: Array<Kind>,
        publicKey: HexKey
    ): Flow<List<LocalNostrEvent>>

    @RawQuery
    fun  observePublicKeyFollowingNostrEvents(
        query: RoomRawQuery
    ): Flow<List<LocalNostrEvent>>

    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND savedAt < strftime('%s', 'now') ORDER BY savedAt DESC")
    fun  getAllNostrEvents(kinds: Array<Kind>): List<NostrEvent>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun observeNostrEventById(id: String): Flow<LocalNostrEvent?>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun getNostrEventById(id: String): NostrEvent?

    @Query("SELECT * FROM NostrEvent WHERE pubKey = :publicKey AND kind = :kind")
    fun getNostrEventByPublicKeyAndKind(publicKey: HexKey, kind: Kind): NostrEvent?

    @Upsert
    suspend fun upsert(nostrEvent: NostrEvent)

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(nostrEvent: NostrEvent)
}