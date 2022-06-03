package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.*
import com.demo.chat.secure.AuthMetaPrincipleByKeySearch
import com.demo.chat.secure.AuthMetaTargetByKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AbstractAuthorizationService
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.service.security.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.random.Random

@Profile("security")
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class ChatSecurityApp {
    companion object {
        val ANONYMOUS_KEY: Key<Long> = Key.funKey(0L)
        val atomicLong = AtomicLong(kotlin.math.abs(Random.nextLong(1024, 999999)))
        private val keyGen = Supplier { Key.funKey(atomicLong.incrementAndGet()) }

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatSecurityApp>(*args)
        }
    }

    @Bean
    fun appReady(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwdStore: SecretsStore<Long, String>,
        authenticationManager: SampleAuthenticationManager,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>>

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
            app.doSomethingNeedingAuth()
            app.doSomethingMessagy()
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }

    @Autowired
    private lateinit var app: ChatSecurityApp

    @Secured("ROLE_SUPER")
    fun doSomethingNeedingAuth() {
        println("SUPER!")
    }

    @Secured("ROLE_MESSAGE")
    fun doSomethingMessagy() {
        // sink.add(message)
    }

    @Bean
    fun authenticationManager(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passStore: SecretsStore<Long, String>,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>>
    ) =
        SampleAuthenticationManager(
            chatAuthenticationService(userIndex, passStore),
            userPersistence,
            authorizationService
        )

    @Bean
    fun keyService(): IKeyService<Long> = KeyServiceInMemory { atomicLong.incrementAndGet() }

    @Bean
    fun userPersistence(keySvc: IKeyService<Long>) = UserPersistenceInMemory(keySvc) { u -> u.key }

    @Bean
    fun userIndex(): IndexService<Long, User<Long>, IndexSearchRequest> = LuceneIndex(
        { t ->
            listOf(
                Pair("handle", t.handle),
                Pair("name", t.name)
            )
        },
        { q -> Key.funKey(q.toLong()) },
        { t -> t.key })

    @Bean
    fun authMetaPersistence(keySvc: IKeyService<Long>):
            PersistenceStore<Long, AuthMetadata<Long, String>> =
        AuthMetaPersistenceInMemory(keySvc) { t -> t.key }

    @Bean
    fun authMetaIndex(): IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest> =
        AuthMetaIndexLucene { q -> Key.funKey(q.toLong()) }

    @Bean
    fun authorizationService(
        authPersist: PersistenceStore<Long, AuthMetadata<Long, String>>,
        authIndex: IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest>
    ): AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>> =
        AbstractAuthorizationService(
            authPersist,
            authIndex,
            AuthMetaPrincipleByKeySearch,
            AuthMetaTargetByKeySearch,
            { ANONYMOUS_KEY },
            { m -> m.key },
            AuthSummarizer { a, b -> (a.key.id - b.key.id).toInt() }
        )

    @Bean
    fun passwordStore(): SecretsStore<Long, String> = SecretsStoreInMemory()

    @Bean
    fun chatAuthenticationService(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        secretsStore: SecretsStore<Long, String>
    ) = AbstractAuthenticationService(
        userIndex,
        secretsStore,
        { input, secure -> input == secure },
        { user: String -> IndexSearchRequest(UserIndexService.HANDLE, user, 1) })

    class SampleAuthenticationManager(
        private val authenticationS: AuthenticationService<Long, String, String>,
        private val userPersistence: PersistenceStore<Long, User<Long>>,
        private val authorizationS: AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>>
    ) :
        AuthenticationManager {
        override fun authenticate(authen: Authentication): Authentication {
            val credential = authen.credentials.toString()
            val targetId: Key<Long> = Key.funKey(if (authen.details is Long) authen.details as Long else 0L)

            return authenticationS
                .authenticate(authen.name, credential)
                .onErrorMap { thr -> InternalAuthenticationServiceException(thr.message, thr) }
                .flatMap(userPersistence::get)
                .flatMap { user ->
                    authorizationS
                        .getAuthorizationsAgainst(user.key, targetId)
                        .map { authMeta -> authMeta.permission }
                        .collect(Collectors.toList())
                        .map { authorizations ->
                            val userDetails = ChatUserDetails(user, authorizations)
                            UsernamePasswordAuthenticationToken(
                                userDetails,
                                authen.credentials,
                                userDetails.authorities
                            ).apply {
                                details = authen.details
                            }
                        }
                }
                .switchIfEmpty(Mono.error(BadCredentialsException("Invalid Credentials")))
                .block()!!
        }
    }
}