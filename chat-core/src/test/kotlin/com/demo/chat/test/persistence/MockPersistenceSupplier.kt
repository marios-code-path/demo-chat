package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.service.PersistenceStore
import com.demo.chat.test.key.MockKeyServiceSupplier
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockPersistenceSupplier {
    inline fun <reified T> keyService() = MockKeyServiceSupplier().get<T>()

    inline fun <reified T, reified E> get(): PersistenceStore<T, E> = mock<PersistenceStore<T, E>>()
            .apply {
                val keyService = keyService<T>()

                given(add(any()))
                        .willReturn(Mono.empty())
                given(rem(any()))
                        .willReturn(Mono.empty())
                given(get(any()))
                        .willReturn(Mono.just(Any() as E))
                given(all())
                        .willReturn(Flux.just(Any() as E))
                given(byIds(Mockito.anyList()))
                        .willReturn(Flux.just(Any() as E))
                given(key())
                        .willReturn(Mono.just(Key.funKey(Any() as T)))//keyService.key(Any::class.java))
            }
}