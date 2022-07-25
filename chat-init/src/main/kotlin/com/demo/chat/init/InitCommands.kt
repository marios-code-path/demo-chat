package com.demo.chat.init

import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Flux

@ShellComponent
class InitCommands<T>(
    private val serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
    private val passwdStore: SecretsStore<T>,
    private val authenticationManager: AuthenticationManager,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>,
    private val keyGen: IKeyGenerator<T>
) {

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

    @ShellMethod("Create a Key")
    fun key():T  {
        return keyGen.nextKey()
    }

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption toUserId: T,
        @ShellOption messageText: String
    ) {
        val keySvc = serviceBeans.keyClient()
        keySvc.key(Message::class.java).flatMap { messageId ->
            serviceBeans.pubsubClient()
                .sendMessage(
                    Message.create(
                        MessageKey.Factory.create(messageId.id, adminKey.id, toUserId),
                        messageText,
                        true
                    )
                )
        }
            .block()

        // ERROR HANDLING
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