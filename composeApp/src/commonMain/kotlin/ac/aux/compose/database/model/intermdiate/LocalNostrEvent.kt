package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.Repost
import androidx.room.Embedded
import androidx.room.Relation

data class LocalNostrEvent(
    @Embedded val nostrEvent: NostrEvent,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile,

    @Relation(
        parentColumn = "id",
        entityColumn = "nostrEventId"
    )
    val repost: Repost? = null,
)