package ac.aux.compose.database.model.traits

import kotlin.time.Instant

interface LocalStoreEntity {
    val savedAt: Instant
}