package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalMention(
    @Embedded val mention: at.torch.compose.database.model.Mention,

    @Relation(
        parentColumns = ["mentionedPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile?,
)
