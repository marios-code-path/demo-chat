package com.demo.chat.test.auth

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.security.AuthorizationService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthorizationServiceTests<T>(
    private val authSvc: AuthorizationService<T, AuthMetadata<T>, IndexSearchRequest>,
    private val authMetaSupplier: Supplier<AuthMetadata<T>>
) {

    @Test
    fun `calling method authorize doesnt error`() {
        StepVerifier
            .create(authSvc.authorize(authMetaSupplier.get(), true))
            .verifyComplete()
    }

    @Test
    fun `calling method findAuthorizationsFor doesnt error`() {
        val principal = authMetaSupplier.get().principal
        StepVerifier
            .create(authSvc.getAuthorizationsForPrincipal(principal).collectList())
            .assertNext { list ->
                Assertions
                    .assertThat(list)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `calling method findAuthorizationsAgainst doesnt error`() {
        val principal = authMetaSupplier.get().principal
        val target = authMetaSupplier.get().target

        StepVerifier
            .create(authSvc.getAuthorizationsAgainst(principal, target).collectList())
            .assertNext { list ->
                Assertions
                    .assertThat(list)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `saved authorization should return with getAuthorizationsFor(uid)`() {
        val authMeta = authMetaSupplier.get()
        val principal = authMeta.principal

        val saveAuth = authSvc.authorize(authMeta, true)
        val auths = authSvc.getAuthorizationsForPrincipal(principal)

        val composed = Flux.from(saveAuth).thenMany(auths)

        StepVerifier
            .create(composed)
            .thenConsumeWhile {
                it.principal == principal
            }
            .verifyComplete()
    }

    @Test
    fun `saved authorization should return with getAuthorizationsAgainst(uid, tid)`() {
        val authMeta = authMetaSupplier.get()
        val principal = authMeta.principal
        val target = authMeta.target

        val saveAuth = authSvc.authorize(authMeta, true)
        val auths = authSvc.getAuthorizationsAgainst(principal, target)

        val composed = Flux.from(saveAuth).thenMany(auths)

        StepVerifier
            .create(composed)
            .thenConsumeWhile {
                it.target == target
            }
            .verifyComplete()
    }
}