package ac.aux.compose.database.model.traits

import kotlin.time.Instant

interface SoftDeletableEntity {
    val deletedAt: Instant?
}