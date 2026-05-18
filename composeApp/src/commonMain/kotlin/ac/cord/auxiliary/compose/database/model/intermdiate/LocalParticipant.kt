package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalParticipant(
    @Embedded val participant: Participant,

    @Relation(
        parentColumn = "participantPublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,
)
