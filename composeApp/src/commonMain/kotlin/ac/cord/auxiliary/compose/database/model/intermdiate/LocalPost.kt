package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalPost(
    @Embedded val post: Post,
    @Relation(
        parentColumn = "profilePublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile
)
