package com.demo.chat.secure.service

import com.demo.chat.domain.AuthenticationException
import com.demo.chat.domain.Key
import com.demo.chat.service.PasswordStore
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

// TODO: implement with a Persistence Store
class PasswordStoreInMemory<T, V> : PasswordStore<T, V> {
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