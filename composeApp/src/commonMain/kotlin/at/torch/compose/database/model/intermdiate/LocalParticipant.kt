package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalParticipant(
    @Embedded val participant: at.torch.compose.database.model.Participant,

    @Relation(
        parentColumns = ["participantPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile?,
)
