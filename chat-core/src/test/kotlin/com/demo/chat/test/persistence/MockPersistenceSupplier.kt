package com.demo.chat.test.persistence

import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.TestBase
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockPersistenceSupplier (){
    inline fun <reified T, reified E> get(): PersistenceStore<T, E> = mock<PersistenceStore<T, E>>()
            .apply {
                given(add(TestBase.anyObject()))
                        .willReturn(Mono.empty())
                given(rem(TestBase.anyObject()))
                        .willReturn(Mono.empty())
                given(get(TestBase.anyObject()))
                        .willReturn(Mono.empty())
                given(all())
                        .willReturn(Flux.empty())
                given(byIds(Mockito.anyList()))
                        .willReturn(Flux.empty())
                given(key())
                        .willReturn(Mono.empty())
            }
}