package ac.cord.auxiliary.compose.database.model.traits

import kotlin.time.Instant

interface BroadcastableEntity {
    val broadcastedAt: Instant?
}