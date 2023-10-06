package com.demo.chat.auth.client

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.KeyValueStore
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import reactor.core.publisher.Mono

@Profile("client-kv-store")
class KeyValueStoreRegisteredClientRepository<T>(
    val kvIndex: KeyValueIndexService<T, IndexSearchRequest>,
    val kvStore: KeyValueStore<T, Any>,
    val typeUtil: TypeUtil<T>
) : RegisteredClientRepository {

    override fun save(registeredClient: RegisteredClient) {
        Mono.just(
            KeyValuePair.create(
                Key.funKey(typeUtil.fromString(registeredClient.id)),
                registeredClient as Any
            )
        )
            .doOnNext { t -> kvStore.add(t) }
            .doOnNext { t -> kvIndex.add(t) }
            .then()
            .subscribe()
    }

    override fun findById(id: String): RegisteredClient? =
        kvIndex.findUnique(IndexSearchRequest("id", id, 100))
            .flatMap {
                kvStore.typedGet(it, RegisteredClient::class.java)
                    .map {
                        it.data
                    }
            }.block()

    override fun findByClientId(clientId: String): RegisteredClient? =
        kvIndex.findUnique(IndexSearchRequest("client_id", clientId, 100))
            .flatMap {
                kvStore.typedGet(it, RegisteredClient::class.java)
                    .map {
                        it.data
                    }
            }.block()
}