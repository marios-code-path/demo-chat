package com.demo.chat.test.auth

import com.demo.chat.service.security.AuthorizationService
import org.mockito.kotlin.mock

class MockAuthorizationServiceSupplier {
    fun <T, M> get(): AuthorizationService<T, M> = mock()
}