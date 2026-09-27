package com.demo.chat.controller.core.mapping

import com.demo.chat.controller.resolve.Verified
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

/**
 * The credential routes. A credential belongs to a user, so each owner key
 * verifies in USER before a credential read, write, or comparison. See
 * `CHAT-avduuqwp`, D8 and E18.
 */
interface SecretsStoreMapping<T> : SecretsStore<T> {
    fun verifier(): KeyVerifier<T>

    @MessageMapping("get")
    fun getRoute(@Verified(ChatDomain.USER) key: VerifiedKey<T>): Mono<String> = getStoredCredentials(key.key)

    @MessageMapping("add")
    fun addRoute(keyCredential: KeyCredential<T>): Mono<Void> =
        verifier().verify(keyCredential.key, ChatDomain.USER).then(Mono.defer { addCredential(keyCredential) })

    @MessageMapping("compare")
    fun compareRoute(keyCredential: KeyCredential<T>): Mono<Boolean> =
        verifier().verify(keyCredential.key, ChatDomain.USER).flatMap { compareSecret(keyCredential) }
}
