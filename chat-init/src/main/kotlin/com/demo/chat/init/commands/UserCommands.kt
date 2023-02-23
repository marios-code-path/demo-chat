package com.demo.chat.init.commands

import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.AdminKey
import com.demo.chat.domain.knownkey.AnonymousKey
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Mono
import java.util.function.Supplier

@Profile("shell")
@ShellComponent
class UserCommands<T>(
    private val userService: ChatUserService<T>,
    private val serviceBeans: CoreClientsConfiguration<T, String, IndexSearchRequest>,
    private val passwdStore: SecretsStore<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: Supplier<AnonymousKey<T>>,
    private val adminKey: Supplier<AdminKey<T>>,
    private val keyGen: IKeyGenerator<T>,
    private val keyService: IKeyService<T>
) : CommandsUtil<T>(typeUtil){

    @ShellMethod("Create a Key")
    fun key(@ShellOption(defaultValue = "false") local: String): T {
        return when(local) {
            "true" -> return keyGen.nextKey()
            else -> return keyService.key(Key::class.java).block()!!.id
        }
    }

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
            .map<String> { user ->
                "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"
            }
            .reduce { t, u -> t + u }
            .block()
    }

    @ShellMethod("Find a user")
    fun findUser(@ShellOption handle: String) = serviceBeans
        .userIndexClient()
        .findBy(IndexSearchRequest(UserIndexService.HANDLE, handle, 100)).take(1)
        .flatMap(serviceBeans.userPersistenceClient()::get)
        .doOnNext { user ->
            println("USER: ${user.key.id} / ${user.handle}")
        }
        .blockLast()

    @ShellMethod("Get a user")
    fun getUser(@ShellOption handle: String): String? = userService
        .findByUsername(ByHandleRequest(handle))
        .map { user ->
            "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"
        }
        .blockLast()

    @ShellMethod("Change User Password")
    fun passwd(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption password: String
    ): String {
        userService.findByUserId(ByIdRequest(identity(userId)))
            .doOnNext {
                println("user: ${it.key.id}: ${it.handle}")
            }
            .switchIfEmpty(Mono.error(NotFoundException))
            .flatMap { passwdStore.addCredential(KeyCredential(it.key, password)) }
            .doFinally {
                println("Password Changed.")
            }
            .block()


        return "OK"
    }

    @ShellMethod("Gets user Permissions")
    fun getPermissionsForUser(
        @ShellOption(defaultValue = "_") userId: String,
    ) {
        println("ID | actor -> target | permission | expiration Timestamp (past mean")
        authorizationService
            .getAuthorizationsForPrincipal(Key.funKey(identity(userId)))
            .doOnNext { auth ->
                println("${auth.key.id} | ${auth.principal.id} -> ${auth.target.id} | ${auth.permission} | expires ${auth.expires}")
            }
            .blockLast()
    }

    @ShellMethod("Get all Perms")
    fun allPermissions() = serviceBeans.authMetadataPersistenceClient().all()
        .doOnNext { auth ->
            println("ID: ${auth.key.id} | ${auth.principal.id} -> ${auth.target.id} | ${auth.permission} | expires ${auth.expires}")
        }
        .blockLast()

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
            .doOnNext { auth ->
                println("ID: ${auth.key.id} | ${auth.principal.id} -> ${auth.target.id} | ${auth.permission} | expires ${auth.expires}")
            }
            .flatMap { auth ->
                authorizationService.authorize(auth, true)
            }
            .block()
    }
}