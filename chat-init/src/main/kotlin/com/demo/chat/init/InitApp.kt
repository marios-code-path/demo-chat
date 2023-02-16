package com.demo.chat.init

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.CompositeClientsConfiguration
import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.AuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import reactor.core.publisher.Mono

/**
 * Test Class
 * TODO: turn this into a command 'bootstrap'
 */
@Profile("init")
@Configuration
@EnableConfigurationProperties(BootstrapProperties::class, RSocketClientProperties::class)
@Import(
    // Serialization
    JacksonAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    // TYPES
    TypeUtilConfiguration::class,
    // Transport Security
    TransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    DefaultRequesterFactory::class,
    ClientConfiguration::class,
    CoreClientsConfiguration::class,
    CompositeClientsConfiguration::class,
    AuthConfiguration::class
)
class InitApp{

    // TODO: NOTE - the anonymous key used here should be ignored.
    // This program will generate the anonymous key and admin keys
    @Bean
    fun <T> initOnce(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        bootstrapProperties: BootstrapProperties,
        typeUtil: TypeUtil<T>
    ): CommandLineRunner = CommandLineRunner {

        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

            val thisUserKey = Mono.from(userService.addUser(
                UserCreateRequest(
                    thisUser.name,
                    thisUser.handle,
                    thisUser.imageUri
                )
            ))
                .defaultIfEmpty(emptyKey)
                ?.block()

            identityKeys[identity] = thisUserKey!!
        }

        val anonKey = identityKeys["anonymous"]!!
        val rootKey = identityKeys["root"]!!

        // set roles
        bootstrapProperties
            .users["anonymous"]!!.roles.forEach { permission ->
                authorizationService
                    .authorize(
                        StringRoleAuthorizationMetadata(
                            emptyKey,
                            anonKey,
                            anonKey,
                            permission.role
                        ), true
                    )
                    .block()
            }

        bootstrapProperties
            .users["root"]!!.roles.forEach { permission ->
            authorizationService
                .authorize(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        rootKey,
                        anonKey,
                        permission.role
                    ), true
                )
                .block()
        }
        println("Summary::(Save the keys for later use)")
        println("""
            |Anonymous Key: ${anonKey.id}
            |Root Key: ${rootKey.id}
        """.trimMargin())
    }
}