package com.demo.chat.init

import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@ShellComponent
class InitCommands<T>(
    private val serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
    private val passwdStore: SecretsStore<T>,
    private val authenticationManager: AuthenticationManager,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>
) {

    @ShellMethod("Add A User")
    fun addUser(
        @ShellOption name: String,
        @ShellOption handle: String,
        @ShellOption imageUri: String
    ): String {
        return serviceBeans.keyClient().key(User::class.java)
            .map { key -> User.create(key, name, handle, imageUri) }
            .map { user -> typeUtil.toString(user.key.id) }
            .block()!!
    }

    @ShellMethod("Change User Password")
    fun passwd(
        @ShellOption id: T,
        @ShellOption passwd: String
    ): String {
        serviceBeans.userPersistenceClient().byIds(listOf(Key.funKey(id)))
            .last()
            .flatMap { passwdStore.addCredential(it.key, passwd) }
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