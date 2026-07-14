package press.mantra.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform