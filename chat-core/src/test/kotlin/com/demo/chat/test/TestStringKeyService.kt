package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

class TestStringKeyGen : IKeyGenerator<String> {
    override fun nextId(): String = randomAlphaNumeric(10)
}

class TestUUIDKeyGenerator : IKeyGenerator<UUID> {
    override fun nextId(): UUID = UUID.randomUUID()
}

class TestLongKeyGenerator : IKeyGenerator<Long> {
    private val atom = AtomicLong(abs(Random.nextLong()))

    override fun nextId(): Long = atom.incrementAndGet()
}

class TestGeneratorKeyService<T>(val generator: IKeyGenerator<T>) : IKeyService<T> {
    override fun <S> key(kind: Class<S>): Mono<out Key<T>> =
        Mono.just(Key.funKey(generator.nextId()))

    override fun rem(key: Key<T>): Mono<Void> = Mono.empty()

    override fun exists(key: Key<T>): Mono<Boolean> = Mono.just(true)
}

class TestStringKeyService : IKeyService<String>, IKeyGenerator<String> {
    override fun <S> key(kind: Class<S>): Mono<out Key<String>> =
        Mono.just(Key.funKey(nextId()))

    override fun rem(key: Key<String>): Mono<Void> = Mono.empty()
    override fun exists(key: Key<String>): Mono<Boolean> = Mono.just(true)
    override fun nextId(): String = randomAlphaNumeric(10)
}

class TestUUIDKeyService : IKeyService<UUID>, IKeyGenerator<UUID> {
    override fun <S> key(kind: Class<S>): Mono<out Key<UUID>> =
        Mono.just(Key.funKey(nextId()))

    override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
    override fun exists(key: Key<UUID>): Mono<Boolean> = Mono.just(true)
    override fun nextId(): UUID = UUID.randomUUID()
}

class TestLongKeyService : IKeyService<Long>, IKeyGenerator<Long> {
    private val atom = AtomicLong(abs(Random.nextLong()))

    override fun <S> key(kind: Class<S>): Mono<out Key<Long>> = Mono.just(Key.funKey(nextId()))
    override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
    override fun exists(key: Key<Long>): Mono<Boolean> = Mono.just(true)
    override fun nextId(): Long = atom.incrementAndGet()
}