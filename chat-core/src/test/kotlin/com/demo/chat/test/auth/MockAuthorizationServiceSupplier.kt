package com.demo.chat.test.auth

import com.demo.chat.service.security.AuthorizationService
import com.nhaarman.mockitokotlin2.mock

class MockAuthorizationServiceSupplier {
    fun <T, M> get(): AuthorizationService<T, M, M> = mock()
}