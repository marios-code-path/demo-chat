package com.demo.chat.test.auth

import com.demo.chat.service.AuthService
import com.demo.chat.service.AuthorizationMeta
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class AuthorizationTests<T>(
    private val authSvc: AuthService<T>,
    private val authMetaSupplier: Supplier<AuthorizationMeta<T>>
) {
    @Test
    fun `authorize a user`() {
        val metadata = authMetaSupplier.get()

        StepVerifier
            .create(authSvc.authorize(metadata.uid, metadata.target, metadata.permission, true))
            .verifyComplete()
    }

    @Test
    fun `find authorizationMetadata for a key`() {
        val metadata = authMetaSupplier.get()

        StepVerifier
            .create(authSvc.findAuthorizationsFor(metadata.target))
            .assertNext { meta ->
                Assertions
                    .assertThat(meta)
                    .isNotNull
            }
            .verifyComplete()
    }
}