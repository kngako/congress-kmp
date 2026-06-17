package at.torch.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform