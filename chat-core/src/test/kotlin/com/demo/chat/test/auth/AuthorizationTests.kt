package com.demo.chat.test.auth

import com.demo.chat.service.AuthorizationService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthorizationTests<M, T>(
    private val authSvc: AuthorizationService<T, M>,
    private val authMetaSupplier: Supplier<M>,
    private val uidSupply: Supplier<T>
) {
    @Test
    fun `should test method authorize doesnt error`() {
        StepVerifier
            .create(authSvc.authorize(authMetaSupplier.get(), true))
            .verifyComplete()
    }

    @Test
    fun `should test method findAuthorizationsFor doesnt error`() {
        StepVerifier
            .create(authSvc.findAuthorizationsFor(uidSupply.get()))
            .assertNext { meta ->
                Assertions
                    .assertThat(meta)
                    .isNotNull
            }
            .verifyComplete()
    }
}