package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import androidx.room.Embedded
import androidx.room.Relation

data class LocalProfile(
    @Embedded val nostrEvent: NostrEvent,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?
)
