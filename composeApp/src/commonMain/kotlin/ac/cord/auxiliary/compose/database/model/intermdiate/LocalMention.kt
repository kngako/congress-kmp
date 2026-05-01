package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalMention(
    @Embedded val mention: Mention?,

    @Relation(
        parentColumn = "mentionedPublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,
)
