package ac.cord.auxiliary.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform