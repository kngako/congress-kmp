package press.mantra.compose.network

object UserAgent {
    const val APP_NAME = "Mantra"
    const val CLIENT_NAME = "Mantra"
}

fun String.asClientTag(): Array<String> = arrayOf(
    "client",
    this
)