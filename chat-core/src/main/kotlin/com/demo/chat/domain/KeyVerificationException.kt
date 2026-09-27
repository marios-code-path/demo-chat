package com.demo.chat.domain

/**
 * A key failed verification. Its id is not in the registry, its root differs
 * from the stored root, or its domain differs from the expected domain. See
 * `KeyVerifier` and `CHAT-avduuqwp`.
 */
class KeyVerificationException(message: String) : ChatException(message)
