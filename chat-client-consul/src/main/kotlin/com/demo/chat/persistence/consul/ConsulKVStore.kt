package com.demo.chat.persistence.consul

import com.demo.chat.service.core.InitializingKVStore
import com.ecwid.consul.v1.ConsulClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.Charset

/**
 * A string keyed store over the Consul KV API. Every name is placed under
 * [pathPrefix]. It holds no domain key, because a Consul name has no domain.
 * See `CHAT-avduuqwp`.
 */
class ConsulKVStore(private val client: ConsulClient, private val pathPrefix: String) : InitializingKVStore {

    private fun path(name: String): String = "$pathPrefix/$name"

    override fun read(name: String): Mono<String> = Mono.defer {
        Mono.justOrEmpty(client.getKVValue(path(name)).value?.getDecodedValue(Charset.defaultCharset()))
    }

    override fun write(name: String, value: String): Mono<Void> = Mono.defer {
        client.setKVValue(path(name), value)
        Mono.empty<Void>()
    }

    override fun remove(name: String): Mono<Void> = Mono.defer {
        client.deleteKVValue(path(name))
        Mono.empty<Void>()
    }

    override fun names(): Flux<String> = Flux.defer {
        Flux.fromIterable(client.getKVKeysOnly(pathPrefix).value ?: emptyList())
    }
}
