package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalParticipant(
    @Embedded val participant: press.mantra.compose.database.model.Participant,

    @Relation(
        parentColumns = ["participantPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: press.mantra.compose.database.model.Profile?,
)
