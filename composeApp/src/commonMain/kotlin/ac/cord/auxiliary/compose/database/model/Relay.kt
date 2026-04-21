package ac.cord.auxiliary.compose.database.model

import androidx.room.Entity

@Entity(
    primaryKeys = ["publicKey", "type", "url"]
)
data class Relay(
    val publicKey: String,
    val type: String,
    val url: String,
    val read: Boolean,
    val write: Boolean
)
