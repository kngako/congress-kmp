package ac.aux.compose.network.sockets

typealias SocketConnectionOpenedCallback = (url: String) -> Unit
typealias SocketConnectionClosedCallback = (url: String, error: Throwable?) -> Unit
