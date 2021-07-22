package com.demo.chat.test.auth

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthorizationService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthorizationServiceTests<M, T>(
    val authSvc: AuthorizationService<T, M, M>,
    val authMetaSupplier: Supplier<out M>,
    val uidSupply: Supplier<out Key<T>>
) {

    @Test
    fun `calling method authorize doesnt error`() {
        StepVerifier
            .create(authSvc.authorize(authMetaSupplier.get(), true))
            .verifyComplete()
    }

    @Test
    fun `calling method findAuthorizationsFor doesnt error`() {
        StepVerifier
            .create(authSvc.getAuthorizationsFor(uidSupply.get()).collectList())
            .assertNext { list ->
                Assertions
                    .assertThat(list)
                    .isNotNull
                    .isNotEmpty
            }
            .verifyComplete()
    }

    @Test
    fun `calling method findAuthorizationsAgainst doesnt error`() {
        StepVerifier
            .create(authSvc.getAuthorizationsFor(uidSupply.get()).collectList())
            .assertNext { list ->
                Assertions
                    .assertThat(list)
                    .isNotNull
                    .isNotEmpty
            }
            .verifyComplete()
    }

    @Test
    fun `saved authorization should return with getAuthorizationsFor(uid)`() {
        val saveAuth = authSvc.authorize(authMetaSupplier.get(), true)
        val auths = authSvc.getAuthorizationsFor(uidSupply.get())

        val composed = Flux.from(saveAuth).thenMany(auths).doOnNext(System.out::println)

        StepVerifier
            .create(composed)
            .verifyComplete()
    }

    @Test
    fun `saved authorization should return with getAuthorizationsAgainst(uid, tid)`() {
        val saveAuth = authSvc.authorize(authMetaSupplier.get(), true)
        val auths = authSvc.getAuthorizationsAgainst(uidSupply.get(), uidSupply.get())

        val composed = Flux.from(saveAuth).thenMany(auths)

        StepVerifier
            .create(composed)
            .expectNextCount(1)  // ANON && USER
            .verifyComplete()
    }
}