package at.torch.compose.database.model.traits

import kotlin.time.Instant

interface UserViewableEntity {
    val viewedAt: Instant?
}