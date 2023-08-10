package com.demo.chat.shell.commands

import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
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

@Profile("shell")
@ShellComponent
class UserCommands<T>(
    private val userService: ChatUserService<T>,
    private val serviceBeans: CoreClientsConfiguration<T, String, IndexSearchRequest>,
    private val passwdStore: SecretsStore<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    private val keyGen: IKeyGenerator<T>,
    private val keyService: IKeyService<T>,
    rootKeys: RootKeys<T>,
    private val passwordEncoder: PasswordEncoder
) : CommandsUtil<T>(typeUtil, rootKeys) {

    @ShellMethod("Create a Key")
    fun key(@ShellOption(defaultValue = "false") local: String): T =
        when (local) {
            "true" -> keyGen.nextId()
            else -> keyService.key(Key::class.java).block()!!.id
        }


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
        return serviceBeans.userPersistenceClient().all()
            .map(::userToString)
            .reduce { t, u -> t + u }
            .block()
    }

    @ShellMethod("Find a user")
    fun findUser(@ShellOption handle: String): String? = serviceBeans
        .userIndexClient()
        .findBy(IndexSearchRequest(UserIndexService.HANDLE, handle, 100)).take(1)
        .flatMap(serviceBeans.userPersistenceClient()::get)
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
            serviceBeans
                .authMetadataPersistenceClient().all()
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
        val keySvc = serviceBeans.keyClient()
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