package at.torch.compose.network

object UserAgent {
    const val APP_NAME = "Torch"
    const val CLIENT_NAME = "Torch"
}

fun String.asClientTag(): Array<String> = arrayOf(
    "client",
    this
)