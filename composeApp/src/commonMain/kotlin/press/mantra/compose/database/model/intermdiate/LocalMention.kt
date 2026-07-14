package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalMention(
    @Embedded val mention: press.mantra.compose.database.model.Mention,

    @Relation(
        parentColumns = ["mentionedPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: press.mantra.compose.database.model.Profile?,
)
