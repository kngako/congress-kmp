package press.mantra.compose.database.model.traits

import kotlin.time.Instant

interface BroadcastableEntity {
    val broadcastedAt: Instant?
}