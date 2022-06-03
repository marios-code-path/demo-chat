package com.demo.chat.service.security

import com.demo.chat.domain.AuthenticationException
import com.demo.chat.domain.Key
import com.demo.chat.service.security.SecretsStore
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class SecretsStoreInMemory<T, V> : SecretsStore<T, V> {
    private val secureMap = ConcurrentHashMap<T, V>()

    override fun getStoredCredentials(key: Key<T>): Mono<V> =
        Mono.create { sink ->
            if (secureMap.containsKey(key.id))
                sink.success(secureMap[key.id])
            else
                sink.error(AuthenticationException("Cannot Find Credential for  ${key.id}."))
        }

    override fun addCredential(key: Key<T>, credential: V): Mono<Void> =
        Mono.create { sink ->
            secureMap[key.id] = credential
            sink.success()
        }
}