package com.demo.chat.persistence.consul

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.service.core.InitializingKVStore
import com.ecwid.consul.v1.ConsulClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import java.util.*

class ConsulKVStore(private val client: ConsulClient, private val pathPrefix: String) :
    InitializingKVStore {

    private fun prefixedId(key: Key<String>): String = "$pathPrefix/${key.id}"

    override fun key(): Mono<out Key<String>> = Mono.empty()

    override fun all(): Flux<out KeyDataPair<String, String>> = Flux.defer {
        Optional.ofNullable(client.getKVKeysOnly(pathPrefix).value)
            .orElseGet { emptyList() }
            .map { key -> KeyDataPair.create(Key.funKey(key), "") }
            .let { Flux.fromIterable(it) }
    }

    override fun get(key: Key<String>): Mono<out KeyDataPair<String, String>> = Mono.defer {
        client.getKVValue(prefixedId(key)).value.getDecodedValue(Charset.defaultCharset())
            .let {
                Mono.just(KeyDataPair.create(key, it))
            }
    }

    override fun rem(key: Key<String>): Mono<Void> = Mono.defer {
        client.deleteKVValue(prefixedId(key))
        Mono.empty<Void>()
    }

    override fun add(ent: KeyDataPair<String, String>): Mono<Void> = Mono.defer {
        client.setKVValue(prefixedId(ent.key), ent.data)
        Mono.empty<Void>()
    }
}