package com.demo.chat.test.auth

import com.demo.chat.service.AuthenticationService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthenticationServiceTests<T>(
    private val svc: AuthenticationService<T>,
    private val uidSupply: Supplier<T>
) {
    @Test
    fun `creates authentication`() {
        StepVerifier
            .create(svc.createAuthentication(uidSupply.get(), ""))
            .expectComplete()
    }

    @Test
    fun `should authenticate`() {
        StepVerifier
            .create(svc.authenticate("", ""))
            .expectComplete()
    }
}