package com.demo.chat.secure

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.*
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.memory.auth.AuthenticationServiceImpl
import com.demo.chat.service.impl.memory.auth.AuthorizationInMemory
import com.demo.chat.service.impl.memory.auth.PasswordStoreInMemory
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.random.Random


class ChatSecurityApp {
    companion object {
        const val ANONYMOUS_UID:Long = 0L

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatSecurityApp>(*args)
        }
    }

    @Bean
    fun appReady(
        index: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwdStore: PasswordStore<Long, String>,
        authenticationManager: SampleAuthenticationManager,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, StringRoleAuthorizationMetadata<Long>>

    ): CommandLineRunner = CommandLineRunner { args ->
        val user = User.create(Key.funKey(2L), "mario", "mario", "http://foo")

        userPersistence
            .add(user)
            .doFinally { println("Added a user to persistence.") }
            .block()

        Flux.just(
            StringRoleAuthorizationMetadata(ANONYMOUS_UID, 2L, "ROLE_REQUEST"),
            StringRoleAuthorizationMetadata(2L, 2L, "ROLE_WRITE")
        )
            .flatMap { meta ->
                authorizationService.authorize(meta, true)
            }

        index
            .add(user)
            .doFinally { println("Added a user to index.") }
            .block()
        passwdStore
            .addCredential(user.key, "password")
            .doFinally { println("Added a password.") }
            .block()

        println("username: ")
        val username = readLine()
        println("password: ")
        val password = readLine()
        println("target: (number 1 - 10)")
        val targetObject = readLine()

        try {
            val request = UsernamePasswordAuthenticationToken(username, password).apply { details = targetObject!!.toLong() }
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result

            println("Success : ${SecurityContextHolder.getContext().authentication}")
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        }
    }


    fun doSomethingNeedingAuth() {
        println("HERE!")
    }

    @Bean
    fun authenticationManager(
        index: IndexService<Long, User<Long>, IndexSearchRequest>,
        pass: PasswordStore<Long, String>,
        persistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, StringRoleAuthorizationMetadata<Long>>
    ) =
        SampleAuthenticationManager(authenticationService(index, pass), persistence, authorizationService)

    @Bean
    fun keyService(): IKeyService<Long> = KeyServiceInMemory(Supplier { Random.nextLong() })

    @Bean
    fun userPersistence(keySvc: IKeyService<Long>) = UserPersistenceInMemory(keySvc) { u -> u.key }

    @Bean
    fun authorizationService() = AuthorizationInMemory<Long, StringRoleAuthorizationMetadata<Long>>(
        { m -> m.uid },
        { m -> m.target },
        { ANONYMOUS_UID })

    @Bean
    fun index(): IndexService<Long, User<Long>, IndexSearchRequest> = LuceneIndex(
        { t ->
            listOf(
                Pair("handle", t.handle),
                Pair("name", t.name)
            )
        },
        { q -> Key.funKey(q.toLong()) },
        { t -> t.key })

    @Bean
    fun passwordStore(): PasswordStore<Long, String> = PasswordStoreInMemory()

    @Bean
    fun authenticationService(
        index: IndexService<Long, User<Long>, IndexSearchRequest>,
        passwordStore: PasswordStore<Long, String>
    ) = AuthenticationServiceImpl(
        index,
        passwordStore,
        { input, secure -> input == secure },
        { user: String -> IndexSearchRequest(UserIndexService.HANDLE, user, 1) })

    class SampleAuthenticationManager(
        private val authInMemory: AuthenticationService<Long, String, String>,
        private val userPersistence: PersistenceStore<Long, User<Long>>,
        private val authorizationService: AuthorizationService<Long, StringRoleAuthorizationMetadata<Long>>
    ) :
        AuthenticationManager {
        override fun authenticate(authen: Authentication): Authentication {
            val credential = authen.credentials.toString()
            val targetId: Long = if(authen.details is Long)  authen.details as Long  else 0L

            return authInMemory
                .authenticate(authen.name, credential)
                .onErrorMap { thr -> InternalAuthenticationServiceException(thr.message, thr) }
                .flatMap(userPersistence::get)
                .flatMap { user ->
                    authorizationService
                        .getAuthorizationsAgainst(user.key.id, targetId)
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