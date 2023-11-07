package com.demo.chat.test.integration

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.User
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.access.core.IKeyServiceAccess
import com.demo.chat.security.access.core.PersistenceAccess
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.TestBase.TestBase.anyObject
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.key.MockKeyGeneratorResolver
import com.demo.chat.test.security.LongUserDetailsConfiguration
import com.demo.chat.test.security.WithLongCustomChatUser
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.stereotype.Service
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testng.Assert
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier


@SpringBootTest(
    classes = [
        TestLongKeyServiceBeans::class,
        LongUserDetailsConfiguration::class,
        TestKeyService::class, TestLongPersistenceBeans::class,
        ChatTestConfiguration::class
    ]
)
class LongMethodSecurityTests(k: IKeyGenerator<Long>) : MethodSecurityTests<Long>(k)

@Disabled
@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
open class MethodSecurityTests<T>(val keyGenerator: IKeyGenerator<T>) {

    @Autowired
    private lateinit var keyService: IKeyService<T>

    @Autowired
    private lateinit var keyServiceBeans: KeyServiceBeans<T>

    @Autowired
    private lateinit var access: SpringSecurityAccessBrokerService<T>

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = [])
    fun `call persistence byIds`(
        @Autowired beans: PersistenceServiceBeans<T, String>,
        @Autowired userPersistence: PersistenceStore<T, User<T>>
    ) {
        val store = beans.userPersistence()

        BDDMockito
            .given(access.hasAccessToMany(anyObject(), anyObject()))
            .willReturn(Mono.just(true))

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
    fun `call persistence add`(
        @Autowired beans: PersistenceServiceBeans<T, String>,
        @Autowired userPersistence: PersistenceStore<T, User<T>>
    ) {
        val store = beans.userPersistence()

        BDDMockito
            .given(access.hasAccessTo(anyObject(), anyObject()))
            .willReturn(Mono.just(true))

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
    fun `call key service for new key, deny access`() {
        val kindClass = String::class.java
        val serviceImpl: IKeyService<T> = keyServiceBeans.keyService()

        val nextKey = keyGenerator.nextKey()

        BDDMockito
            .given(access.hasAccessToDomainByKind<Any>(anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        BDDMockito
            .given(serviceImpl.key(kindClass))
            .willReturn(Mono.just(nextKey))

        StepVerifier
            .create(
                keyService
                    .key(String::class.java)
            )
            .expectError()
            .verify()
    }
}

@Service
class TestKeyService<T>(that: KeyServiceBeans<T>) : IKeyServiceAccess<T>, IKeyService<T> by that.keyService()

@Service
class TestUserPersistence<T>(that: PersistenceServiceBeans<T, *>) : PersistenceAccess<T, User<T>>,
    PersistenceStore<T, User<T>> by that.userPersistence()

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
@EnableReactiveMethodSecurity
class ChatTestConfiguration {

    @Bean
    fun <T> chatAccess(): SpringSecurityAccessBrokerService<T> =
        BDDMockito.mock(SpringSecurityAccessBrokerService::class.java)
                as SpringSecurityAccessBrokerService<T>
}