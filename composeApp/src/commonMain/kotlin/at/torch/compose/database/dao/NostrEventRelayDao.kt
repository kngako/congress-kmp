package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface NostrEventRelayDao {

    @Upsert
    suspend fun upsert(nostrEventRelay: at.torch.compose.database.model.NostrEventRelay)

}