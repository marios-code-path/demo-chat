package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.secure.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.key.MockKeyGeneratorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
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

class SpringSecurityAccessBrokerServiceLongKeyTests(k: IKeyGenerator<Long>) :
    SpringSecurityAccessBrokerServiceTests<Long>(k)

@Disabled
@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
open class SpringSecurityAccessBrokerServiceTests<T>(private val keyGen: IKeyGenerator<T>) {

    val user = User.create(keyGen.nextKey(), "TEST USER", "SOMENAME", "http://test/image.png")
    private val targetKey = keyGen.nextKey()

    private fun roles(): List<String> = emptyList()
    private fun grantedAuthorities() = roles().map { SimpleGrantedAuthority("ROLE_$it") }

    @MockBean
    lateinit var authSvc: AuthorizationService<T, AuthMetadata<T>>

    @MockBean
    lateinit var rootKeys: RootKeys<T>

    @BeforeEach
    fun setUp() {
        val authMetadataAgainstData = Flux.just(
            AuthMetadata.create(
                key = keyGen.nextKey(),
                principal = user.key,
                target = targetKey, perm = "TEST", exp = Long.MAX_VALUE, muted = false
            ),
            AuthMetadata.create(
                key = keyGen.nextKey(),
                principal = user.key,
                target = targetKey, perm = "TEST2", exp = Long.MAX_VALUE, muted = false
            )
        )

        BDDMockito
            .given(authSvc.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(authMetadataAgainstData)
    }

    @Test
    fun `has no access disallow`() {
        val auth =
            UsernamePasswordAuthenticationToken.authenticated(ChatUserDetails(user, roles()), "", grantedAuthorities())

        // ReactorContextTestExecutionListener will set the context on the Hooks.onLastOperator call
        SecurityContextHolder.getContext().authentication = auth

        val broker = AuthMetadataAccessBroker(authSvc)

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        val p = accessService.hasAccessTo(targetKey, "TEST1")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isFalse()
            }
            .verifyComplete()
    }

    @Test
    fun `has access will allow`() {
        val auth =
            UsernamePasswordAuthenticationToken.authenticated(
                ChatUserDetails(user, roles()), "", grantedAuthorities())

        // ReactorContextTestExecutionListener will set the context on the Hooks.onLastOperator call
        SecurityContextHolder.getContext().authentication = auth

        val broker = AuthMetadataAccessBroker(authSvc)

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        val p = accessService.hasAccessTo(targetKey, "TEST")

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