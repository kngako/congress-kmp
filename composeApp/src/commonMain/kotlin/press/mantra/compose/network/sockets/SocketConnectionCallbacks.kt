package press.mantra.compose.network.sockets

typealias SocketConnectionOpenedCallback = (url: String) -> Unit
typealias SocketConnectionClosedCallback = (url: String, error: Throwable?) -> Unit
