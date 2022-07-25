package com.demo.chat.init

import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.rsocket.UnprotectedConnection
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Autowired
    private lateinit var app: App

    @Secured("ROLE_SUPER")
    fun doSomethingNeedingAuth() {
        println("SUPER!")
    }

    @Secured("ROLE_MESSAGE")
    fun doSomethingMessagy() {
        // sink.add(message)
    }

    @Bean
    @Profile("init")
    fun <T> initOnce(
        serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
        passwdStore: SecretsStore<T>,
        authenticationManager: AuthenticationManager,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        anonKey: AnonymousKey<T>,
        adminKey: AdminKey<T>
    ): CommandLineRunner = CommandLineRunner {

        val userIndex = serviceBeans.userIndexClient()
        val userPersistence = serviceBeans.userPersistenceClient()

        val users = listOf(
            User.create(anonKey, "ANONYMOUS", "anonymous", "null"),
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
                adminKey,
                anonKey,
                "ROLE_SUPER"
            ),
        )
            .flatMap { meta -> authorizationService.authorize(meta, true) }
            .doFinally { println("Added Authorizations to Administrator(${anonKey.id}), Anonymous(${anonKey.id}).") }
            .blockLast()


        passwdStore
            .addCredential(KeyCredential( users[0].key, "password"))
            .doFinally { println("Added a password.") }
            .block()
    }

    fun <T> userLogin(
        serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
        passwdStore: SecretsStore<T>,
        authenticationManager: AuthenticationManager,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    ) {

        println("username: ")
        val username = readLine()!!
        println("password: ")
        val password = readLine()!!

        try {
            val userKey =
                serviceBeans.userIndexClient().findBy(IndexSearchRequest(UserIndexService.HANDLE, username, 1))
                    .switchIfEmpty(Mono.error(Exception("NO USER FOUND")))
                    .blockLast()

            val request = UsernamePasswordAuthenticationToken(username, password)
                .apply { details = userKey!!.id }
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result

            app.doSomethingNeedingAuth()
            app.doSomethingMessagy()
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }
}