package ac.aux.compose.network.dto

import ac.aux.compose.database.model.Relay
import kotlinx.serialization.Serializable

@Serializable
data class RelayDTO(
    val url: String,
    val read: Boolean,
    val write: Boolean,
) {

}

fun Relay.mapToRelayDTO() =
    RelayDTO(
        url = this.url,
        read = this.read,
        write = this.write,
    )

fun String.toRelayDTO(): RelayDTO = RelayDTO(url = this, read = true, write = true)