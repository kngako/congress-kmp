package at.torch.compose.database.model.intermdiate

import at.torch.compose.database.model.Post
import at.torch.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalPost(
    @Embedded val post: Post,
    @Relation(
        parentColumns = ["profilePublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: Profile
)
