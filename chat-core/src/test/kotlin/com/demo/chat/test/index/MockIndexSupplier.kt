package com.demo.chat.test.index

import com.demo.chat.service.IndexService
import com.demo.chat.test.TestBase
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockIndexSupplier {
    inline fun <reified T, reified E, reified Q> get(): IndexService<T, E, Q> = mock<IndexService<T, E, Q>>()
        .apply {
                given(add(TestBase.anyObject()))
                        .willReturn(Mono.empty())
                given(rem(TestBase.anyObject()))
                        .willReturn(Mono.empty())
                given(findBy(TestBase.anyObject()))
                        .willReturn(Flux.empty())//Flux.just(Key.funKey(Any() as T)))
        }
}