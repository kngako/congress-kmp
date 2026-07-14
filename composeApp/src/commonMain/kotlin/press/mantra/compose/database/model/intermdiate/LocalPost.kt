package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation
import press.mantra.compose.database.model.Post
import press.mantra.compose.database.model.Profile

data class LocalPost(
    @Embedded val post: Post,
    @Relation(
        parentColumns = ["profilePublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: Profile
)
