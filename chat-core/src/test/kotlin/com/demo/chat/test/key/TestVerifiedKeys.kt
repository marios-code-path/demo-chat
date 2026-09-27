package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.VerifiedKey

/**
 * A verified key for a broker test. It reads no registry. See `CHAT-avduuqwp`.
 *
 * A broker test checks grants, not verification. The boundary tests in
 * `chat-security` prove the verification with a real `KeyVerifier`.
 * `KeyVerifierConstructionTests` limits this constructor to main source.
 */
object TestVerifiedKeys {
    fun <T> of(key: Key<T>): VerifiedKey<T> = VerifiedKey(key)
}

/** The same conversion as [TestVerifiedKeys.of]. */
fun <T> Key<T>.verified(): VerifiedKey<T> = TestVerifiedKeys.of(this)
