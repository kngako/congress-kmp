package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.GENESIS_AT
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import fr.acinq.bitcoin.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface NostrEventDao {
    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) ORDER BY createdAt DESC")
    fun  observeNostrEvents(kinds: Array<Kind>): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE content LIKE '%' || :search || '%' AND kind in (:kinds) ORDER BY createdAt DESC")
    fun  observeFilteredNostrEvents(
        kinds: Array<Kind>,
        search: String
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND id in (:ids) ORDER BY createdAt DESC")
    fun  observeFilteredNostrEvents(
        kinds: Array<Kind>,
        ids: Array<HexKey>,
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND pubKey in (:authors) ORDER BY createdAt DESC LIMIT 50")
    fun  observeAuthoredNostrEvents(
        kinds: Array<Kind>,
        authors: Array<HexKey>,
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE tags LIKE '%' || :publicKey || '%' AND kind in (:kinds) ORDER BY createdAt DESC")
    fun  observePublicKeyMentionedNostrEvents(
        kinds: Array<Kind>,
        publicKey: HexKey
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE tags LIKE '%' || :eventId || '%reply%' AND kind in (:kinds) AND createdAt > :since ORDER BY createdAt DESC")
    fun  observeNostrEventReplies(
        kinds: Array<Kind>,
        eventId: HexKey,
        since: Instant
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @RawQuery
    fun  observePublicKeyFollowingNostrEvents(
        query: RoomRawQuery
    ): Flow<List<LocalNostrEvent>>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getNostrEvents(
        kinds: Array<Kind>,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE content LIKE '%' || :search || '%' AND kind in (:kinds) AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getFilteredNostrEvents(
        kinds: Array<Kind>,
        search: String,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND id in (:ids) AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getFilteredNostrEvents(
        kinds: Array<Kind>,
        ids: Array<HexKey>,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE id in (:ids) AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getNostrEventsByIds(
        ids: Array<HexKey>,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) AND pubKey in (:authors)  AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getAuthoredNostrEvents(
        kinds: Array<Kind>,
        authors: Array<HexKey>,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE tags LIKE '%' || :publicKey || '%' AND kind in (:kinds)  AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getPublicKeyMentionedNostrEvents(
        kinds: Array<Kind>,
        publicKey: HexKey,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE tags LIKE '%' || :eventId || '%reply%' AND kind in (:kinds) AND createdAt > :since ORDER BY createdAt ASC LIMIT :limit")
    fun  getNostrEventReplies(
        kinds: Array<Kind>,
        eventId: HexKey,
        since: Instant,
        limit: Int
    ): List<NostrEvent>

    @Query("SELECT * FROM NostrEvent WHERE kind in (:kinds) ORDER BY savedAt DESC")
    fun  getAllNostrEvents(kinds: Array<Kind>): List<NostrEvent>

    @Transaction
    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun observeNostrEventById(id: String): Flow<LocalNostrEvent?>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun getNostrEventById(id: String): NostrEvent?

    @Query("SELECT * FROM NostrEvent WHERE pubKey = :publicKey AND kind = :kind ORDER BY createdAt DESC")
    fun getNostrEventByPublicKeyAndKind(publicKey: HexKey, kind: Kind): NostrEvent?

    @Upsert
    suspend fun upsert(nostrEvent: NostrEvent)

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(nostrEvent: NostrEvent)
}