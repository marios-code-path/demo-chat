package com.demo.chat.test.auth

import com.demo.chat.service.AuthorizationService
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockAuthorizationServiceSupplier() {
    inline fun <reified M, reified T> get(): AuthorizationService<M, T> =
        mock<AuthorizationService<M, T>>().apply {
                given(authorize(any(), any()))
                    .willReturn(Mono.create {
                        it.success()
                    })
                given(findAuthorizationsFor(any()))
                    .willReturn(Flux.empty())
            }
}