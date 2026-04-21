package ac.cord.auxiliary.compose.exceptions

class SigningRejectedException(message: String? = null, cause: Throwable? = null) : SignatureException(message, cause) {
}