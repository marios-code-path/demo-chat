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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longTypeUtil(): TypeUtil<Long> = TypeUtil.LongUtil

    @Value("\${app.service.identity.anonymous}")
    private lateinit var anonymousId: String

    @Value("\${app.service.identity.admin}")
    private lateinit var adminId: String

    data class AnonymousKey<T>(override val id: T) : Key<T>
    data class AdminKey<T>(override val id: T) : Key<T>

    @Bean
    fun <T> anonymousKey(typeUtil: TypeUtil<T>) = AnonymousKey(typeUtil.fromString(anonymousId))

    @Bean
    fun <T> adminKey(typeUtil: TypeUtil<T>) = AdminKey(typeUtil.fromString(adminId))

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
    class ServiceClientConfiguration<T>(serviceDefinitions: CoreRSocketServiceDefinitions<T, String, IndexSearchRequest>) :
        ServiceBeanConfiguration<T, String, IndexSearchRequest>(serviceDefinitions)

    @Bean
    fun <T> rSocketBoundServices(
        requesterFactory: RequesterFactory,
        clientRSocketProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ) = CoreRSocketServiceDefinitions<T, String, IndexSearchRequest>(
        requesterFactory,
        clientRSocketProps,
        typeUtil
    )

    @Bean
    fun <T> passwdStore(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()

    @Configuration
    class AppAuthConfiguration<T>(typeUtil: TypeUtil<T>, anonKey: AnonymousKey<T>) :
        AuthConfiguration<T>(keyTypeUtil = typeUtil, anonKey)

    @Bean
    fun <T> appReady(
        serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
        passwdStore: SecretsStore<T>,
        authenticationManager: AuthenticationManager,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        anonKey: AnonymousKey<T>,
        adminKey: AdminKey<T>
    ): CommandLineRunner = CommandLineRunner {

        val userIndex = serviceBeans.userIndexClient()
        val userPersistence = serviceBeans.userPersistenceClient()

        val princpialKey = userPersistence.key().block()!!
        val anotherUserKey = userPersistence.key().block()!!

        val users = listOf(
            User.create(princpialKey, "test", "test", "https://foo"),
            User.create(anonKey, "ANON", "anonymous", "null"),
            User.create(adminKey, "ADMIN", "administrator", "http://localhost/icons/admin.png")
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
                anonKey,
                anonKey,
                "ROLE_REQUEST"
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                anonKey,
                princpialKey,
                "ROLE_REQUEST",
                1
            ),
            StringRoleAuthorizationMetadata(
                userPersistence.key().block()!!,
                anonKey,
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
            .doFinally { println("Added Authorizations to principal($princpialKey), anotherUser($anotherUserKey) and anon ($anonKey).") }
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