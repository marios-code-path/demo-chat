package com.demo.chat.security.rsocket

enum class RSocketSecurityErrorKind {
    AUTHENTICATION,
    AUTHORIZATION,
}

data class RSocketSecurityErrorPayload(
    val version: Int,
    val kind: RSocketSecurityErrorKind,
)

sealed class CoreSecurityRefusal(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CoreAuthenticationRefusal(cause: Throwable) : CoreSecurityRefusal(
    "The core refused authentication.",
    cause,
)

class CoreAuthorizationRefusal(cause: Throwable) : CoreSecurityRefusal(
    "The core refused authorization.",
    cause,
)
