package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface NostrEventRelayDao {

    @Upsert
    suspend fun upsert(nostrEventRelay: press.mantra.compose.database.model.NostrEventRelay)

}