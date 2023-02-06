package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.randomAlphaNumeric
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.*
import kotlin.random.Random

class MockKeyServiceSupplier {

    inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)!!

    inline fun <reified T> get(): IKeyService<T> = mock<IKeyService<T>>()
            .apply {
                given(this.exists(any()))
                        .willReturn(Mono.just(true))
                given(this.rem(any()))
                        .willReturn(Mono.empty())

                when (T::class) {
                    Any::class -> given(this.key<Any>(any()))
                            .willReturn(Mono.just(Key.funKey(Any() as T)))
                    UUID::class -> given(this.key<Any>(any()))
                            .willReturn(Mono.just(Key.funKey(UUID.randomUUID() as T)))
                    String::class -> given(this.key<Any>(any()))
                            .willReturn(Mono.just(Key.funKey(randomAlphaNumeric(48) as T)))
                    Number::class, Int::class, Long::class -> given(this.key<Any>(any()))
                            .willReturn(Mono.just(Key.funKey(Random(System.currentTimeMillis()).nextLong() as T)))
                }
            }
}