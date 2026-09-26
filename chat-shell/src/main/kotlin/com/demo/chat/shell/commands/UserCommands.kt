package com.demo.chat.shell.commands

import com.demo.chat.domain.UnsupportedDomainException

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Profile("shell")
@Component
class UserCommands<T : Any>(
    private val coreServices: CoreServices<T, String, IndexSearchRequest>,
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
    private val passwordEncoder: PasswordEncoder
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val userService: ChatUserService<T> = compositeServices.userService()
    private val passwdStore: SecretsStore<T> = coreServices.secretsStore()

    /**
     * An id that a user types resolves through the server registry, so each
     * call carries a key with its stored root. See `CHAT-avduuqwp`, C63 to C66.
     */
    private val verifier = KeyVerifier(coreServices.keyService(), rootKeys)
    fun kv(value: String): Key<T>? {
        val key = coreServices
            .keyService()
            .key(ChatDomain.KEY_VALUE_PAIR)
            .block()!!

        return coreServices.keyValuePersistence()
            .add(com.demo.chat.domain.KeyValuePair.create(key, value))
            .thenReturn(key)
            .block()
    }
    fun getKV(key: T): String? =
        verifier.resolve(key, ChatDomain.KEY_VALUE_PAIR)
            .flatMap { coreServices.keyValuePersistence().get(it.key) }
            .map { kv -> "${kv.key.id} -> ${kv.data}"}
            .block()
    fun allKV(): MutableList<String>? =
        coreServices
            .keyValuePersistence()
            .all()
            .map { kv -> "${kv.key.id} -> ${kv.data}" }
            .collectList()
            .block()
    /**
     * A key with no domain cannot be minted. The command prints the refusal and
     * returns. See `CHAT-avduuqwp`, the mint table of section C.
     */
    fun key(): String? = UnsupportedDomainException("Key").message

    fun userToString(user: User<T>): String = "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"
    fun addUser(
        name: String,
        handle: String,
        imageUri: String
    ): String? =
        userService
            .addUser(UserCreateRequest(name, handle, imageUri))
            .map { key -> typeUtil.toString(key.id) }
            .block()
    fun users(): String? {
        return coreServices.userPersistence().all()
            .map(::userToString)
            .reduce { t, u -> t + u }
            .block()
    }
    fun findUser(handle: String): String? = coreServices
        .userIndex()
        .findBy(IndexSearchRequest(UserIndexService.HANDLE, handle, 100)).take(1)
        .flatMap(coreServices.userPersistence()::get)
        .map(::userToString)
        .reduce { t, u -> t + u }
        .block()
    fun getUser(handle: String): String? = userService
        .findByUsername(ByStringRequest(handle))
        .doOnNext {
            println("KEY = ${it}")
        }
        .map(::userToString)
        .reduce { t, u -> t + u }
        .block()
    fun passwd(
        userId: String,
        password: String
    ): String? {
        return userService
            .findByUserId(ByIdRequest(identity(userId)))
            .switchIfEmpty(Mono.error(NotFoundException))
            .flatMap { passwdStore.addCredential(KeyCredential(it.key, checkNotNull(passwordEncoder.encode(password)) { "the password encoder returned no value" })) }
            .map { "Password Changed." }
            .block()
    }

    private fun authMetaToString(auth: AuthMetadata<T>): String =
        "${auth.key.id} | ${auth.principal.id} -> ${auth.target.id} | ${auth.permission} | ${auth.mute} | expires ${auth.expires}\n"

    val authMetaHeader = "ID | actor -> target | permission | muted | Timestamp \n"
    fun getPermissionsForUser(userId: String): String? = Flux
        .concat(
            Mono.just(authMetaHeader),
            authorizationService
                .getAuthorizationsForPrincipal(verifier.resolve(identity(userId), ChatDomain.USER).block()!!.key)
                .map(::authMetaToString)
        )
        .reduce { t, u -> t + u }
        .block()
    fun allPermissions(): String? = Flux
        .concat(
            Mono.just(authMetaHeader),
            coreServices
                .authMetaPersistence().all()
                .map(::authMetaToString)
        )
        .reduce { t, u -> t + u }
        .block()

    // e.g. userA -> topicB : "SEND_MESSAGE"
    fun addPermission(
        userId: String,
        targetUserId: String,
        role: String,
        expireTime: String
    ) {
        val keySvc = coreServices.keyService()
        val e: Long = java.lang.Long.parseLong(expireTime)
        val expiryTime = if (e == 1L) Long.MAX_VALUE else e
        keySvc
            .key(ChatDomain.AUTH_METADATA)
            .zipWith(verifier.resolve(identity(userId), ChatDomain.USER))
            .zipWith(verifier.resolve(typeUtil.fromString(targetUserId), null))
            .map { keys ->
                val metadataKey = keys.t1.t1
                StringRoleAuthorizationMetadata(
                    metadataKey,
                    keys.t1.t2.key,
                    keys.t2.key,
                    role,
                    expiryTime
                )
            }
            .flatMap { auth ->
                authorizationService.authorize(auth, true)
            }
            .block()
    }
}