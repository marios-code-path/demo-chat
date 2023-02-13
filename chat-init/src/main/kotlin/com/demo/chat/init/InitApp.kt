package com.demo.chat.init

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.AuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.secure.transport.UnprotectedConnection
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager

/**
 * Test Class
 * TODO: turn this into a command 'bootstrap'
 */
@Profile("init")
@Configuration
class InitApp {

    @Bean
    fun <T> initOnce(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        authenticationManager: AuthenticationManager,
        bootstrapProperties: BootstrapProperties,
        typeUtil: TypeUtil<T>
    ): CommandLineRunner = CommandLineRunner {
        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

            identityKeys[identity] = userService.addUser(
                UserCreateRequest(
                    thisUser.name,
                    thisUser.handle,
                    thisUser.imageUri
                )
            ).block()!!
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
        println("Initialization Complete.")
    }
}