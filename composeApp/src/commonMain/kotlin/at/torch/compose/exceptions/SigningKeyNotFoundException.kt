package at.torch.compose.exceptions

class SigningKeyNotFoundException(message: String? = null, cause: Throwable? = null) : SignatureException(
    message,
    cause,
)
