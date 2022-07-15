package com.demo.chat.init

import com.demo.chat.client.rsocket.config.CoreRSocketServiceDefinitions
import com.demo.chat.client.rsocket.config.DefaultRequesterFactory
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.config.AuthConfiguration
import com.demo.chat.secure.rsocket.TransportFactory
import com.demo.chat.secure.rsocket.UnprotectedConnection
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.security.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import java.util.*

/**
 * Test Class
 */
@SpringBootApplication
@EnableConfigurationProperties(RSocketClientProperties::class)
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    UnprotectedConnection::class
)
@EnableGlobalMethodSecurity(securedEnabled = true)
class SampleAppSecurityRunner {

    @Bean
    fun typeUtil() = UUIDUtil()

    @Bean
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        clientConnectionProps: RSocketClientProperties,
        tcpConnectionFactory: TransportFactory
    ): DefaultRequesterFactory =
        DefaultRequesterFactory(
            builder,
            tcpConnectionFactory,
            clientConnectionProps.config
        )

    @Configuration
    class ServiceClientConfiguration(serviceDefinitions: CoreRSocketServiceDefinitions<UUID, String, IndexSearchRequest>) :
        ServiceBeanConfiguration<UUID, String, IndexSearchRequest>(serviceDefinitions)

    @Bean
    fun rSocketBoundServices(
        requesterFactory: RequesterFactory,
        clientRSocketProps: RSocketClientProperties
    ) = CoreRSocketServiceDefinitions<UUID, String, IndexSearchRequest>(
        requesterFactory,
        clientRSocketProps,
        UUIDUtil()
    )

    @Bean
    fun passwdStore(): UserCredentialSecretsStore<UUID> = SecretsStoreInMemory()

    @Configuration
    class AppAuthConfiguration(val typeUtil: TypeUtil<UUID>) : AuthConfiguration<UUID>(keyTypeUtil = typeUtil, ANONYMOUS_KEY)

    @Bean
    fun appReady(
        serviceBeans: ServiceBeanConfiguration<UUID, String, IndexSearchRequest>,
        passwdStore: SecretsStore<UUID>,
        authenticationManager: AuthenticationManager,
        authorizationService: AuthorizationService<UUID, AuthMetadata<UUID>, AuthMetadata<UUID>>
    ): CommandLineRunner = CommandLineRunner {

        val userIndex  = serviceBeans.userIndexClient()
        val userPersistence = serviceBeans.userPersistenceClient()

        val princpialKey = userPersistence.key().block()!!
        val anotherUserKey = userPersistence.key().block()!!

        val users = listOf(
            User.create(princpialKey, "test", "test", "https://foo"),
            User.create(ANONYMOUS_KEY, "ANON", "anonymous", "null")
        )

        Flux.fromIterable(users)
            .flatMap(userPersistence::add)
            .doFinally { println("Added users to persistence.") }
            .blockLast()

        Flux.fromIterable(users)
            .flatMap(userIndex::add)
            .doFinally { println("Added users to index.") }
            .blockLast()

        Flux.just(
            // role that determines required access? ( key, target, action, role )
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                ANONYMOUS_KEY,
                ANONYMOUS_KEY,
                "ROLE_REQUEST"
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                ANONYMOUS_KEY,
                princpialKey,
                "ROLE_REQUEST",
                1
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                ANONYMOUS_KEY,
                anotherUserKey,
                "ROLE_MESSAGE",
                1
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                princpialKey,
                anotherUserKey,
                "ROLE_MESSAGE"
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                princpialKey,
                anotherUserKey,
                "ROLE_SUPER"
            ),
        )
            .flatMap { meta -> authorizationService.authorize(meta, true) }
            .doFinally { println("Added Authorizations to principal($princpialKey), anotherUser($anotherUserKey) and anon ($ANONYMOUS_KEY).") }
            .blockLast()


        passwdStore
            .addCredential(users[0].key, "password")
            .doFinally { println("Added a password.") }
            .block()


        println("username: ")
        val username = readLine()
        println("password: ")
        val password = readLine()

        try {
            val request = UsernamePasswordAuthenticationToken(username, password)
                .apply { details = anotherUserKey.id }
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result

            println("Success : ${SecurityContextHolder.getContext().authentication}")
            sampleAppSecurityRunner.doSomethingNeedingAuth()
            sampleAppSecurityRunner.doSomethingMessagy()
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }

    companion object {
        val ANONYMOUS_KEY: Key<UUID> = Key.funKey(UUID.randomUUID())

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<SampleAppSecurityRunner>(*args)
        }
    }

    @Autowired
    private lateinit var sampleAppSecurityRunner: SampleAppSecurityRunner

    @Secured("ROLE_SUPER")
    fun doSomethingNeedingAuth() {
        println("SUPER!")
    }

    @Secured("ROLE_MESSAGE")
    fun doSomethingMessagy() {
        // sink.add(message)
    }
}