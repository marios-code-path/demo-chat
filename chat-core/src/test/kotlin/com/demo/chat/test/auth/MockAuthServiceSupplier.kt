package com.demo.chat.test.auth

import com.demo.chat.service.AuthService
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import reactor.core.publisher.Mono


class MockAuthServiceSupplier() {
    inline fun <reified T> get(): AuthService<T> = mock<AuthService<T>>()
        .apply {
            given(authorize(any(), any(), any(), any()))
                .willReturn(Mono.create {
                    it.success()
                })
            given(authenticate(any(), any()))
                .willReturn(Mono.empty())
        }
}