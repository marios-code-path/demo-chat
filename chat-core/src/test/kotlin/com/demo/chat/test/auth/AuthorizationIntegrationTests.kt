package com.demo.chat.test.auth

import com.demo.chat.service.AuthorizationService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier

class AuthorizationIntegrationTests<M, T>(
    private val authSvc: AuthorizationService<T, M, M>,
    private val authMetaSupplier: Supplier<M>,
    private val uidSupply: Supplier<T>
) {


}