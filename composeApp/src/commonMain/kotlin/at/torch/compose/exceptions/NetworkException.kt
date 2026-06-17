package at.torch.compose.exceptions

class NetworkException(
    message: String? = null,
    cause: Throwable? = null
): RuntimeException(message, cause)