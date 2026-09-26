package com.demo.chat.test

import com.demo.chat.test.key.TestVerifiers

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.key.MockKeyGeneratorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
class LongAccessBrokerTests(k: IKeyGenerator<Long>, t: IKeyGenerator<Long>) : AccessBrokerTests<Long>(k, t)

@Disabled
open class AccessBrokerTests<T>(
    private val keyGenerator: IKeyGenerator<T>,
    /**
     * A second generator, so a target never equals the principal. The mock
     * generator answers one key for every call. A key holds every right over
     * itself since CHAT-ixzpkqxg, so one generator would test the self rule.
     */
    private val targetKeyGenerator: IKeyGenerator<T>
) {

    @Test
    fun `empty authMetadata disallows access`() {
        val myPrincipal = TestKeys.key(keyGenerator.nextId())
        val objectForAccess = TestKeys.key(targetKeyGenerator.nextId())
        val authSvc: AuthorizationService<T, AuthMetadata<T>> = BDDMockito.mock()

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject(), anyObject()))
            .willReturn(Flux.empty())

        val access = AuthMetadataAccessBroker(authSvc, TestVerifiers.resolvingNothing())

        StepVerifier
            .create(access.hasAccessByKey(myPrincipal, objectForAccess, "TEST"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isFalse()
            }
            .verifyComplete()
    }

    @Test
    fun `sufficient privileges allows access`() {
        val myPrincipal = TestKeys.key(keyGenerator.nextId())
        val objectForAccess = TestKeys.key(targetKeyGenerator.nextId())
        val authSvc: AuthorizationService<T, AuthMetadata<T>> = BDDMockito.mock()

        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = TestKeys.key(keyGenerator.nextId()),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST", exp = Long.MAX_VALUE
            ),
            AuthMetadata.create(
                key = TestKeys.key(keyGenerator.nextId()),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST2", exp = Long.MAX_VALUE
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)

        val access = AuthMetadataAccessBroker(authSvc, TestVerifiers.resolvingNothing())

        StepVerifier
            .create(access.hasAccessByKey(myPrincipal, objectForAccess, "TEST"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `insufficient privileges disallows access`() {
        val myPrincipal = TestKeys.key(keyGenerator.nextId())
        val objectForAccess = TestKeys.key(targetKeyGenerator.nextId())
        val authSvc: AuthorizationService<T, AuthMetadata<T>> = BDDMockito.mock()

        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = TestKeys.key(keyGenerator.nextId()),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST", exp = Long.MAX_VALUE
            ),
            AuthMetadata.create(
                key = TestKeys.key(keyGenerator.nextId()),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST2", exp = Long.MAX_VALUE
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)

        val access = AuthMetadataAccessBroker(authSvc, TestVerifiers.resolvingNothing())

        StepVerifier
            .create(access.hasAccessByKey(myPrincipal, objectForAccess, "TEST3"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isFalse()
            }
            .verifyComplete()
    }

}