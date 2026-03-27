package ac.aux.compose.network

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener

class KtorWebsocketListener: WebSocketListener {
    val logger = Logger.withTag("KtorWebsocketListener")

    val out: WebSocketListener? = null

    override fun onOpen(pingMillis: Int, compression: Boolean) {
        logger.d("onOpen: $pingMillis, $compression")
        out?.onOpen(pingMillis, compression)
    }

    override fun onMessage(text: String) {
        logger.d("onMessage: $text")
        out?.onMessage(text)
    }

    override fun onClosed(code: Int, reason: String) {
        logger.d("onClosed: $code, $reason")
        out?.onClosed(code, reason)
    }

    override fun onFailure(t: Throwable, code: Int?, response: String?) {
        logger.e("onFailure: $code, $response", t)
        out?.onFailure(t, code, response)
    }
}