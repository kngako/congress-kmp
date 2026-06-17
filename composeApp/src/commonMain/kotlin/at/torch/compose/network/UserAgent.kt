package at.torch.compose.network

object UserAgent {
    const val APP_NAME = "Aux"
    const val CLIENT_NAME = "Aux"
}

fun String.asClientTag(): Array<String> = arrayOf(
    "client",
    this
)