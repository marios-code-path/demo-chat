package com.demo.chat.service.security

import com.demo.chat.domain.AuthenticationException
import com.demo.chat.domain.Key
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class SecretsStoreInMemory<T> : UserCredentialSecretsStore<T> {
    private val secureMap = ConcurrentHashMap<T, String>()

    override fun getStoredCredentials(key: Key<T>): Mono<String> =
        Mono.create { sink ->
            if (secureMap.containsKey(key.id))
                sink.success(secureMap[key.id])
            else
                sink.error(AuthenticationException("Cannot Find Credential for  ${key.id}."))
        }

    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void> =
        Mono.create { sink ->
            val keyId = keyCredential.key.id
            val credential = keyCredential.credential
            secureMap[keyId] = credential
            sink.success()
        }
}