package com.demo.chat.test.integration

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.GenerateRootKeyInitializer
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.access.composite.UserServiceAccess
import com.demo.chat.security.access.core.IKeyServiceAccess
import com.demo.chat.security.access.core.PersistenceAccess
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.TestBase.TestBase.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.key.MockKeyGeneratorResolver
import com.demo.chat.test.config.TestLongUserDetailsConfiguration
import com.demo.chat.test.config.WithLongCustomChatUser
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.stereotype.Service
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier


@SpringBootTest(
    classes = [
        TestLongKeyServiceBeans::class,
        TestLongPersistenceBeans::class,
        TestLongUserDetailsConfiguration::class,
        TestLongCompositeServiceBeans::class,
        TestKeyService::class,
        MethodSecurityIntegrationTestConfiguration::class
    ]
)

class LongMethodSecurityIntegrationTests(k: IKeyGenerator<Long>) : MethodSecurityIntegrationTests<Long>(k)

@Disabled
@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
open class MethodSecurityIntegrationTests<T>(val keyGenerator: IKeyGenerator<T>) {

    @Autowired
    private lateinit var rootKeys: RootKeys<T>

    @Autowired
    private lateinit var keyService: IKeyService<T>

    @Autowired
    private lateinit var keyServiceBeans: KeyServiceBeans<T>

    @MockBean
    private lateinit var authService: AuthorizationService<T, AuthMetadata<T>>

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = [])
    fun `anonymous call find user allowed`(
        @Autowired composites: CompositeServiceBeans<T, String>,
        @Autowired users: ChatUserService<T>,
    ) {
        val userService = composites.userService()

        val principal = rootKeys.getRootKey(Anon::class.java)
        val objectForAccess = rootKeys.getRootKey(User::class.java)

        val data = AuthMetadata.create(
            key = keyGenerator.nextKey(),
            principal = principal,
            target = objectForAccess, perm = "FIND", muted = false, exp = Long.MAX_VALUE
        )

        BDDMockito.given(authService.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(Flux.just(data))

        BDDMockito
            .given(userService.findByUsername(anyObject()))
            .willReturn(Flux.empty())

        StepVerifier
            .create(users.findByUsername(ByStringRequest("testhandle")))
            .verifyComplete()
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = [])
    @DirtiesContext()
    fun `anonymous call persistence byIds`(
        @Autowired beans: PersistenceServiceBeans<T, String>,
        @Autowired userPersistence: PersistenceStore<T, User<T>>
    ) {
        val store = beans.userPersistence()

        val objectForAccess = keyGenerator.nextKey()

        val data = AuthMetadata.create(
            key = keyGenerator.nextKey(),
            principal = rootKeys.getRootKey(Anon::class.java),
            target = objectForAccess, perm = "GET", muted = false, exp = Long.MAX_VALUE
        )

        BDDMockito.given(authService.getAuthorizationsAgainstMany(anyObject(), anyObject()))
            .willReturn(Flux.just(data))

        BDDMockito
            .given(store.byIds(anyObject()))
            .willReturn(Flux.empty())

        StepVerifier
            .create(
                userPersistence.byIds(listOf(keyGenerator.nextKey()))
            )
            .verifyComplete()
    }


    @Test
    @WithLongCustomChatUser(userId = 1L, roles = [])
    @DirtiesContext()
    fun `call persistence add`(
        @Autowired beans: PersistenceServiceBeans<T, String>,
        @Autowired userPersistence: PersistenceStore<T, User<T>>
    ) {
        val store = beans.userPersistence()

        val principal = rootKeys.getRootKey(Anon::class.java)
        val objectForAccess = rootKeys.getRootKey(User::class.java)
        val data = AuthMetadata.create(
            key = keyGenerator.nextKey(),
            principal = principal,
            target = objectForAccess, perm = "PUT", muted = false, exp = Long.MAX_VALUE
        )
        BDDMockito.given(authService.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(
                Flux.just(data)
            )

        BDDMockito
            .given(store.add(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier
            .create(
                userPersistence.add(User.create(keyGenerator.nextKey(), "test", "test", "test"))
            )
            .verifyComplete()
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = [])
    @DirtiesContext()
    fun `call key service for new key, deny access`() {
        val kindClass = User::class.java
        val serviceImpl: IKeyService<T> = keyServiceBeans.keyService()

        val nextKey = keyGenerator.nextKey()

        val principal = rootKeys.getRootKey(Anon::class.java)
        val objectForAccess = rootKeys.getRootKey(User::class.java)
        val data = AuthMetadata.create(
            key = keyGenerator.nextKey(),
            principal = principal,
            target = objectForAccess, perm = "NON", muted = false, exp = Long.MAX_VALUE
        )

        BDDMockito.given(authService.getAuthorizationsAgainst(anyObject(), anyObject()))
            .willReturn(
                Flux.just(data)
            )

        BDDMockito
            .given(serviceImpl.key(kindClass))
            .willReturn(Mono.just(nextKey))

        StepVerifier
            .create(
                keyService
                    .key(kindClass)
            )
            .verifyError(AccessDeniedException::class.java)

    }
}

@Service
class TestKeyService<T>(that: KeyServiceBeans<T>) : IKeyServiceAccess<T>, IKeyService<T> by that.keyService()

@Service
class TestUserPersistence<T>(that: PersistenceServiceBeans<T, *>) : PersistenceAccess<T, User<T>>,
    PersistenceStore<T, User<T>> by that.userPersistence()

@Service
class TestUserService<T>(that: CompositeServiceBeans<T, *>) : UserServiceAccess<T>,
    ChatUserService<T> by that.userService()

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
@EnableReactiveMethodSecurity
class MethodSecurityIntegrationTestConfiguration {

    @Bean
    fun <T> accessBroker(authSvc: AuthorizationService<T, AuthMetadata<T>>) = AuthMetadataAccessBroker(authSvc)

    @Bean
    fun <T> rootKeys(keyGen: IKeyGenerator<T>): RootKeys<T> = RootKeys<T>().apply {
        GenerateRootKeyInitializer(keyGen).initRootKeys(this)
    }

    @Bean
    fun <T> chatAccess(access: AccessBroker<T>, rootKeys: RootKeys<T>): SpringSecurityAccessBrokerService<T> =
        SpringSecurityAccessBrokerService(access, rootKeys)

}