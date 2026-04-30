package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room.Embedded
import androidx.room.Relation

data class LocalMention(
    @Embedded val mention: Mention,

    @Relation(
        parentColumn = "mentionedPublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,
)
