package ac.cord.auxiliary.compose.network.dto

import ac.cord.auxiliary.compose.database.model.Relay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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