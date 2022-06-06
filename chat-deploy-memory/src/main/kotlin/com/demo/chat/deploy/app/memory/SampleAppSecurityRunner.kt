package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.*
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.random.Random

/**
 * Test Class
 */
@Profile("security")
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class SampleAppSecurityRunner {

    @Bean
    fun appReady(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwdStore: SecretsStore<Long>,
        authenticationManager: SampleAuthenticationManager,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long>, AuthMetadata<Long>>

    ): CommandLineRunner = CommandLineRunner {
        val princpialKey = keyGen.get()
        val anotherUserKey = keyGen.get()

        val users = listOf(
            User.create(princpialKey, "mario", "mario", "https://foo"),
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
            StringRoleAuthorizationMetadata(keyGen.get(), ANONYMOUS_KEY, ANONYMOUS_KEY, "ROLE_REQUEST"),
            StringRoleAuthorizationMetadata(keyGen.get(), ANONYMOUS_KEY, princpialKey, "ROLE_REQUEST", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), ANONYMOUS_KEY, anotherUserKey, "ROLE_MESSAGE", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), princpialKey, anotherUserKey, "ROLE_MESSAGE"),
            StringRoleAuthorizationMetadata(keyGen.get(), princpialKey, anotherUserKey, "ROLE_SUPER"),
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
        val ANONYMOUS_KEY: Key<Long> = Key.funKey(0L)
        inline fun <reified T> anonymousKeyFetcher(): () -> Key<T> = { Key.funKey( 0L as T)}
        val atomicLong = AtomicLong(kotlin.math.abs(Random.nextLong(1024, 999999)))
        private val keyGen = Supplier { Key.funKey(atomicLong.incrementAndGet()) }

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