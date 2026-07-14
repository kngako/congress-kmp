package press.mantra.compose.network.sockets

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

interface NostrSocketClient {
    val socketUrl: String

    val incomingMessages: SharedFlow<NostrIncomingMessage>

    suspend fun close()

    @Throws(
        Exception::class, // TODO: Use NetworkExtension
        CancellationException::class,
    )
    suspend fun ensureSocketConnectionOrThrow()

    suspend fun sendAUTH(signedEvent: JsonObject)

    suspend fun sendCLOSE(subscriptionId: String)

    suspend fun sendCOUNT(data: JsonObject): String

    suspend fun sendEVENT(signedEvent: JsonObject)

    suspend fun sendREQ(subscriptionId: String, data: JsonObject)

    suspend fun sendMESSAGE(text: String, ensureSessionBeforeSend: Boolean = true)
}
