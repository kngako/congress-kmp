package at.torch.compose.database.model.traits

import kotlin.time.Instant

interface TimestampedEntity {
    val createdAt: Instant
    val updatedAt: Instant
}