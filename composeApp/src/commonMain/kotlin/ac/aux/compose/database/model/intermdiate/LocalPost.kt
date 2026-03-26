package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import androidx.room.Embedded
import androidx.room.Relation

data class LocalPost(
    @Embedded val post: Post,
    @Relation(
        parentColumn = "profilePublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile
)
