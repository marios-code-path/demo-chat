package com.demo.chat.secure.app

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.secure.AuthFilterizer
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.service.*
import com.demo.chat.service.*
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
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
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.random.Random


@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class ChatSecurityApp {
    companion object {
        val ANONYMOUS_KEY: Key<Long> = Key.funKey(0L)

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatSecurityApp>(*args)
        }
    }

    @Bean
    fun appReady(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwdStore: PasswordStore<Long, String>,
        authenticationManager: SampleAuthenticationManager,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>>

    ): CommandLineRunner = CommandLineRunner { args ->
        val keyGenerator = Supplier { Key.funKey(Random(3894329L).nextLong()) }
        val princpialKey = Key.funKey(2L)
        val anotherUserKey = Key.funKey(Random(1384).nextInt(2580).toLong())

        val users = listOf(
            User.create(princpialKey, "mario", "mario", "http://foo"),
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
            StringRoleAuthorizationMetadata(keyGenerator.get(), ANONYMOUS_KEY, princpialKey, "ROLE_REQUEST"),
            StringRoleAuthorizationMetadata(keyGenerator.get(), princpialKey, princpialKey, "ROLE_WRITE"),
            StringRoleAuthorizationMetadata(keyGenerator.get(), ANONYMOUS_KEY, ANONYMOUS_KEY, "ROLE_OPTIONAL"),
            StringRoleAuthorizationMetadata(keyGenerator.get(), princpialKey, anotherUserKey, "ROLE_SUPER")
        )
            .flatMap { meta -> authorizationService.authorize(meta, true) }
            .doFinally { println("Added Authorizations to users.") }
            .blockLast()


        passwdStore
            .addCredential(users[0].key, "password")
            .doFinally { println("Added a password.") }
            .block()

        println("username: ")
        val username = readLine()
        println("password: ")
        val password = readLine()
        println("target: (number 1 - 10)")
        val targetObject = readLine()

        try {
            val request = UsernamePasswordAuthenticationToken(username, password)
                .apply { details = targetObject!!.toLong() }
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result

            println("Success : ${SecurityContextHolder.getContext().authentication}")
            app.doSomethingNeedingAuth()
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : "  + e.message)
        }
    }

    @Autowired
    private lateinit var app: ChatSecurityApp

    @Secured("ROLE_SUPER")
    fun doSomethingNeedingAuth() {
        println("HERE!")
    }

    @Bean
    fun authenticationManager(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passStore: PasswordStore<Long, String>,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>>
    ) =
        SampleAuthenticationManager(authenticationService(userIndex, passStore), userPersistence, authorizationService)

    @Bean
    fun keyService(): IKeyService<Long> = KeyServiceInMemory(Supplier { Random.nextLong() })

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
        AuthorizationPersistenceInMemory

    @Bean
    fun authMetaIndex(): IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest> =
        AuthorizationMetaIndexInMemory

    @Bean
    fun authorizationService(
        authPersist: PersistenceStore<Long, AuthMetadata<Long, String>>,
        authIndex: IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest>,
    ): AuthorizationService<Long, AuthMetadata<Long, String>, AuthMetadata<Long, String>> =
        AbstractAuthorizationService(
            authPersist,
            authIndex,
            AuthPrincipleByKeySearch,
            { ANONYMOUS_KEY },
            { m -> m.key },
            AuthFilterizer()
        )

    @Bean
    fun passwordStore(): PasswordStore<Long, String> = PasswordStoreInMemory()

    @Bean
    fun authenticationService(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwordStore: PasswordStore<Long, String>
    ) = AuthenticationServiceImpl(
        userIndex,
        passwordStore,
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
            println("The Target Id is: $targetId")
            return authenticationS
                .authenticate(authen.name, credential)
                .onErrorMap { thr -> InternalAuthenticationServiceException(thr.message, thr) }
                .flatMap(userPersistence::get)
                .flatMap { user ->
                    authorizationS
                        .getAuthorizationsAgainst(user.key, targetId)
                        .doOnNext(System.out::println)
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