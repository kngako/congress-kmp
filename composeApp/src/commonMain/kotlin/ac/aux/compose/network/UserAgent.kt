package ac.aux.compose.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

object UserAgent {
    const val APP_NAME = "Aux"
    const val CLIENT_NAME = "Aux"
}

fun String.asClientTag(): Array<String> = arrayOf(
    "client",
    this
)