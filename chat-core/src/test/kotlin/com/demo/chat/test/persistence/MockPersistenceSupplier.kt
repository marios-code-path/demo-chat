package com.demo.chat.test.persistence

import com.demo.chat.service.PersistenceStore
import com.demo.chat.test.key.MockKeyServiceSupplier
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockPersistenceSupplier (){
    inline fun <reified T> keyService() = MockKeyServiceSupplier().get<T>()

    inline fun <reified T, reified E> get(): PersistenceStore<T, E> = mock<PersistenceStore<T, E>>()
            .apply {
                val keyService = keyService<T>()

                given(add(any()))
                        .willReturn(Mono.empty())
                given(rem(any()))
                        .willReturn(Mono.empty())
                given(get(any()))
                        .willReturn(Mono.empty())
                given(all())
                        .willReturn(Flux.empty())
                given(byIds(Mockito.anyList()))
                        .willReturn(Flux.empty())
                given(key())
                        .willReturn(keyService.key(Any::class.java))
            }
}