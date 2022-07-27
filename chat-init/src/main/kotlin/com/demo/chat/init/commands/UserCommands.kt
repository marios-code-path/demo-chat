package com.demo.chat.init.commands

import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.*
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Flux

@ShellComponent
class UserCommands<T>(
    private val serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
    private val passwdStore: SecretsStore<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>,
    private val keyGen: IKeyGenerator<T>
) {

    @ShellMethod("Create a Key")
    fun key():T  {
        return keyGen.nextKey()
    }

    @ShellMethod("Add A User")
    fun addUser(
        @ShellOption name: String,
        @ShellOption handle: String,
        @ShellOption imageUri: String
    ): String {
        val keyId = keyGen.nextKey()
        val newUser = User.create(Key.funKey(keyId), name, handle, imageUri)
        Flux.just(newUser)
            .flatMap { user ->
                serviceBeans.userPersistenceClient().add(user)
                    .flatMap {
                        serviceBeans.userIndexClient().add(user)
                    }
            }
            .blockLast()
        return typeUtil.toString(keyId)
    }

    @ShellMethod("All Users")
    fun users(): String? {
        return serviceBeans.userPersistenceClient().all()
            .map<String> { user ->
                "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"
            }
            .reduce {t, u -> t + u}
            .block()
    }

    @ShellMethod("Get a user")
    fun getUser(@ShellOption handle: String) : String {
        return serviceBeans.userIndexClient().findBy(IndexSearchRequest(UserIndexService.HANDLE, handle, 100))
            .flatMap  {
                serviceBeans.userPersistenceClient().byIds(listOf(it))
            }
            .map { user ->
                "${user.key.id}: ${user.handle}, ${user.name}, ${user.imageUri}\n"
            }
            .blockLast()!!
    }

    @ShellMethod("Change User Password")
    fun passwd(
        @ShellOption id: T,
        @ShellOption passwd: String
    ): String {
        serviceBeans.userPersistenceClient().byIds(listOf(Key.funKey(id)))
            .last()
            .flatMap { passwdStore.addCredential(KeyCredential(it.key, passwd)) }
            .block()!!

        return "OK"
    }

    // e.g. userA -> topicB : "SEND_MESSAGE"
    @ShellMethod("Add a User Permission")
    fun permission(
        @ShellOption userId: T,
        @ShellOption targetUserId: T,
        @ShellOption role: String
    ) {
        val keySvc = serviceBeans.keyClient()
        keySvc
            .key(AuthMetadata::class.java)
            .flatMap { metadataKey ->
                authorizationService.authorize(
                    StringRoleAuthorizationMetadata(
                        metadataKey,
                        Key.funKey(userId),
                        Key.funKey(targetUserId),
                        role,
                        Long.MAX_VALUE
                    ),
                    true
                )
            }
            .block()
    }
}