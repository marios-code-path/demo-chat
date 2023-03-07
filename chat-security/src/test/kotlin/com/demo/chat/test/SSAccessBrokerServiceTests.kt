package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.secure.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.key.MockKeyGeneratorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
class LongSSAccesBrokerServiceTests(k: IKeyGenerator<Long>) : SSAccessBrokerServiceTests<Long>(k)


@Disabled
open class SSAccessBrokerServiceTests<T>(private val keyGen: IKeyGenerator<T>) {

    val user = User.create(keyGen.nextKey(), "TEST USER", "SOMENAME", "http://test/image.png")
    private fun roles() = listOf("WRITE", "READ")
    private fun grantedAuthorities() = roles().map { SimpleGrantedAuthority("ROLE_$it") }

    @MockBean
    lateinit var authSvc: AuthorizationService<T, AuthMetadata<T>>

    @Test
    fun `has access will allow`() {
        val auth =
            UsernamePasswordAuthenticationToken.authenticated(ChatUserDetails(user, roles()), "", grantedAuthorities())

        // ReactorContextTestExecutionListener will set the context on the Hooks.onLastOperator call
        SecurityContextHolder.getContext().authentication = auth

        val targetKey = keyGen.nextKey()
        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = keyGen.nextKey(),
                principal = user.key,
                target = targetKey, perm = "TEST", exp = Long.MAX_VALUE
            ),
            AuthMetadata.create(
                key = keyGen.nextKey(),
                principal = user.key,
                target = targetKey, perm = "TEST2", exp = Long.MAX_VALUE
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)

        val broker = AuthMetadataAccessBroker(authSvc)

        val accessService = SpringSecurityAccessBrokerService(broker)

        val p = accessService.hasAccessFor(targetKey, "TEST")


        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isTrue()
            }
            .verifyComplete()

    }
}