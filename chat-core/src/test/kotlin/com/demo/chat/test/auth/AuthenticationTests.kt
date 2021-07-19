package com.demo.chat.test.auth

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthenticationService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthenticationServiceTests<T, E, V>(
    private val svc: AuthenticationService<T, E, V>,
    private val uidSupply: Supplier<Key<T>>,
    private val uNameSupply: Supplier<E>,
    private val pwSupply: Supplier<V>
) {
    @Test
    fun `should call createAuthentication doesnt error`() {
        StepVerifier
            .create(svc.setAuthentication(uidSupply.get(), pwSupply.get()))
            .verifyComplete()
    }

    @Test
    fun `should call authenticate doesnt error`() {
        StepVerifier
            .create(svc.authenticate(uNameSupply.get(), pwSupply.get()))
            .verifyComplete()
    }
}