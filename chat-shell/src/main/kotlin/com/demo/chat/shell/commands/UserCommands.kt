package com.demo.chat.shell.commands

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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Profile("shell")
@ShellComponent
class UserCommands<T>(
    private val coreServices: CoreServices<T, String, IndexSearchRequest>,
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
    private val passwordEncoder: PasswordEncoder
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val userService: ChatUserService<T> = compositeServices.userService()
    private val passwdStore: SecretsStore<T> = coreServices.secretsStore()

    @ShellMethod("Create a KeyValue")
    fun kv(@ShellOption value: String): Key<T>? {
        val key = coreServices
            .keyService()
            .key(KeyValuePair::class.java)
            .block()!!

        return coreServices.keyValuePersistence()
            .add(com.demo.chat.domain.KeyValuePair.create(key, value))
            .thenReturn(key)
            .block()
    }

    @ShellMethod("Get a KeyValue by Key ID")
    fun getKV(@ShellOption key: T): String? =
        coreServices
            .keyValuePersistence()
            .get(Key.funKey(key))
            .map { kv -> "${kv.key.id} -> ${kv.data}"}
            .block()

    @ShellMethod("Get all KV")
    fun allKV(): MutableList<String>? =
        coreServices
            .keyValuePersistence()
            .all()
            .map { kv -> "${kv.key.id} -> ${kv.data}" }
            .collectList()
            .block()

    @ShellMethod("Create a Key")
    fun key(): T? =
        coreServices.keyService().key(Key::class.java).block()?.id

    fun userToString(user: User<T>): String = "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"

    @ShellMethod("Add A User")
    fun addUser(
        @ShellOption name: String,
        @ShellOption handle: String,
        @ShellOption imageUri: String
    ): String? =
        userService
            .addUser(UserCreateRequest(name, handle, imageUri))
            .map { key -> typeUtil.toString(key.id) }
            .block()

    @ShellMethod("All Users")
    fun users(): String? {
        return coreServices.userPersistence().all()
            .map(::userToString)
            .reduce { t, u -> t + u }
            .block()
    }

    @ShellMethod("Find a user")
    fun findUser(@ShellOption handle: String): String? = coreServices
        .userIndex()
        .findBy(IndexSearchRequest(UserIndexService.HANDLE, handle, 100)).take(1)
        .flatMap(coreServices.userPersistence()::get)
        .map(::userToString)
        .reduce { t, u -> t + u }
        .block()

    @ShellMethod("Get a user")
    fun getUser(@ShellOption handle: String): String? = userService
        .findByUsername(ByStringRequest(handle))
        .doOnNext {
            println("KEY = ${it}")
        }
        .map(::userToString)
        .reduce { t, u -> t + u }
        .block()

    @ShellMethod("Change User Password")
    fun passwd(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption password: String
    ): String? {
        return userService
            .findByUserId(ByIdRequest(identity(userId)))
            .switchIfEmpty(Mono.error(NotFoundException))
            .flatMap { passwdStore.addCredential(KeyCredential(it.key, passwordEncoder.encode(password))) }
            .map { "Password Changed." }
            .block()
    }

    private fun authMetaToString(auth: AuthMetadata<T>): String =
        "${auth.key.id} | ${auth.principal.id} -> ${auth.target.id} | ${auth.permission} | ${auth.mute} | expires ${auth.expires}\n"

    val authMetaHeader = "ID | actor -> target | permission | muted | Timestamp \n"

    @ShellMethod("Gets user Permissions")
    fun getPermissionsForUser(@ShellOption(defaultValue = "_") userId: String): String? = Flux
        .concat(
            Mono.just(authMetaHeader),
            authorizationService
                .getAuthorizationsForPrincipal(Key.funKey(identity(userId)))
                .map(::authMetaToString)
        )
        .reduce { t, u -> t + u }
        .block()

    @ShellMethod("Get all Perms")
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
    @ShellMethod("Add a User Permission")
    fun addPermission(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption targetUserId: String,
        @ShellOption role: String,
        @ShellOption expireTime: String
    ) {
        val keySvc = coreServices.keyService()
        val e: Long = java.lang.Long.parseLong(expireTime)
        val expiryTime = if (e == 1L) Long.MAX_VALUE else e
        keySvc
            .key(AuthMetadata::class.java)
            .map { metadataKey ->
                StringRoleAuthorizationMetadata(
                    metadataKey,
                    Key.funKey(identity(userId)),
                    Key.funKey(typeUtil.fromString(targetUserId)),
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