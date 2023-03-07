package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.key.MockKeyGeneratorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
class LongAccessBrokerTests(k: IKeyGenerator<Long>) : AccessBrokerTests<Long>(k)

@Disabled
open class AccessBrokerTests<T>(
    private val keyGenerator: IKeyGenerator<T>
) {

    @MockBean
    lateinit var authSvc: AuthorizationService<T, AuthMetadata<T>>

    @Test
    fun `Empty authMetadata disallows access`() {
        val myPrincipal = keyGenerator.nextKey()
        val objectForAccess = keyGenerator.nextKey()

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(Flux.empty())

        val access = AuthMetadataAccessBroker(authSvc)

        StepVerifier
            .create(access.getAccess(myPrincipal, objectForAccess, "TEST"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isFalse()
            }
            .verifyComplete()
    }

    @Test
    fun `sufficient privileges allows access`() {
        val myPrincipal = keyGenerator.nextKey()
        val objectForAccess = keyGenerator.nextKey()

        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = keyGenerator.nextKey(),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST", exp = Long.MAX_VALUE
            ),
            AuthMetadata.create(
                key = keyGenerator.nextKey(),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST2", exp = Long.MAX_VALUE
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)

        val access = AuthMetadataAccessBroker(authSvc)

        StepVerifier
            .create(access.getAccess(myPrincipal, objectForAccess, "TEST"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `insufficient privileges disallows access`() {
        val myPrincipal = keyGenerator.nextKey()
        val objectForAccess = keyGenerator.nextKey()

        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = keyGenerator.nextKey(),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST", exp = Long.MAX_VALUE
            ),
            AuthMetadata.create(
                key = keyGenerator.nextKey(),
                principal = myPrincipal,
                target = objectForAccess, perm = "TEST2", exp = Long.MAX_VALUE
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)

        val access = AuthMetadataAccessBroker(authSvc)

        StepVerifier
            .create(access.getAccess(myPrincipal, objectForAccess, "EXEC"))
            .assertNext { ret ->
                Assertions
                    .assertThat(ret)
                    .isFalse()
            }
            .verifyComplete()
    }

}