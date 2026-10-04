package com.demo.chat.test.integration

import com.demo.chat.test.key.TestVerifiers

import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestRoots

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.access.composite.UserServiceAccess
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.TestBase.TestBase.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.key.MockKeyGeneratorResolver
import com.demo.chat.test.config.TestLongUserDetailsConfiguration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.stereotype.Service
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import org.springframework.security.test.context.support.WithAnonymousUser


@SpringBootTest(
    classes = [
        TestLongKeyServiceBeans::class,
        TestLongPersistenceBeans::class,
        TestLongUserDetailsConfiguration::class,
        TestLongCompositeServiceBeans::class,
        MethodSecurityIntegrationTestConfiguration::class
    ]
)
@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
class LongMethodSecurityIntegrationTests(k: IKeyGenerator<Long>) : MethodSecurityIntegrationTests<Long>(k)

@Disabled
open class MethodSecurityIntegrationTests<T>(val keyGenerator: IKeyGenerator<T>) {

    @Autowired
    private lateinit var rootKeys: RootKeys<T>

    @MockitoBean
    private lateinit var authService: AuthorizationService<T, AuthMetadata<T>>

    /**
     * **`@WithAnonymousUser` is what makes this call anonymous.**
     *
     * This test ran with no security context until 2026-09-23, and the read
     * answered the `Anon` root key for an absent context. So the test passed
     * while stating nothing about an anonymous caller.
     *
     * Absence denies now, and anonymous is a decision. See
     * `docs/IDENTITY-POLICY.md`.
     */
    @Test
    @WithAnonymousUser
    fun `anonymous call find user allowed`(
        @Autowired composites: CompositeServiceBeans<T, String>,
        @Autowired users: ChatUserService<T>,
    ) {
        val userService = composites.userService()

        val principal = rootKeys.anon()
        val objectForAccess = rootKeys.of(ChatDomain.USER)

        val data = AuthMetadata.create(
            key = TestKeys.key(keyGenerator.nextId()),
            principal = principal,
            target = objectForAccess, perm = "FIND", muted = false, exp = Long.MAX_VALUE
        )

        BDDMockito.given(authService.getAuthorizationsAgainst(anyObject(), anyObject(), anyObject()))
            .willReturn(Flux.just(data))

        BDDMockito
            .given(userService.findByUsername(anyObject()))
            .willReturn(Flux.empty())

        StepVerifier
            .create(users.findByUsername(ByStringRequest("testhandle")))
            .verifyComplete()
    }

    // The persistence and key service tests were removed with the core access
    // interfaces on 2026-10-04. The RSocket seam rule guards those routes by
    // role, and CoreRouteAccessTests holds the transport proof. See
    // CHAT-wgdnjdio and CHAT-zwopgvkx.
}

@Service
class TestUserService<T>(that: CompositeServiceBeans<T, *>) : UserServiceAccess<T>,
    ChatUserService<T> by that.userService()

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
@EnableReactiveMethodSecurity
class MethodSecurityIntegrationTestConfiguration {

    @Bean
    fun <T> accessBroker(authSvc: AuthorizationService<T, AuthMetadata<T>>) =
        AuthMetadataAccessBroker(authSvc, TestVerifiers.resolvingNothing())

    @Bean
    /**
     * Every concrete test in this context uses Long keys. The roots are distinct
     * fixed ids. The USER root is the fixed test root, because the test users
     * carry it, and a typed user store returns keys under the USER root.
     */
    fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(ChatDomain.USER to Key.root(TestRoots.of(0L))),
        TestKeys.key(8998L),
        TestKeys.key(8999L),
    )

    @Bean
    fun <T> chatAccess(access: AccessBroker<T>, rootKeys: RootKeys<T>): SpringSecurityAccessBrokerService<T> =
        SpringSecurityAccessBrokerService(access, rootKeys, TestVerifiers.acceptingTestRoot(rootKeys))
}
