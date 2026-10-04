package com.demo.chat.service.core

import com.demo.chat.domain.Key

/**
 * A key that `KeyVerifier` accepted. See `CHAT-avduuqwp`.
 *
 * A `VerifiedKey` from `verify` or `resolve` proves that the registry holds
 * the id with that root. A `VerifiedKey` from `domainRoot` is a domain root
 * key that the verifier loaded at startup. The trusted store conversion
 * `trustTypedStore` was removed on 2026-10-04, because its one caller went
 * with the core access interfaces. See `CHAT-wgdnjdio`.
 *
 * The constructor is `internal`, which is a module boundary and not a proof.
 * `KeyVerifierConstructionTests` limits the constructor calls in main source to
 * `KeyVerifier.kt`.
 */
class VerifiedKey<T> internal constructor(val key: Key<T>) {
    override fun equals(other: Any?) = other is VerifiedKey<*> && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = "verified key $key"
}
