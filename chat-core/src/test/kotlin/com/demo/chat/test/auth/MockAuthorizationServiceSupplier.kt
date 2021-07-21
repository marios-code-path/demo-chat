package com.demo.chat.test.auth

import com.demo.chat.service.AuthorizationService
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import com.nhaarman.mockitokotlin2.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockAuthorizationServiceSupplier {
    fun <T, M> get(): AuthorizationService<T, M, M> = mock()
}