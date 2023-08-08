package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.deploy.event.RootKeyUpdatedEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@ConditionalOnProperty("app.users.create", havingValue = "true")
class UserInitializationListener(val publisher: DeploymentEventPublisher) {

    @Bean
    fun <T> initializeUsersService(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>>,
        secretsStore: SecretsStore<T>,
        initializationProperties: UserInitializationProperties,
        passwordEncoder: PasswordEncoder,
        typeUtil: TypeUtil<T>,
    ) = InitialUsersService(userService, authorizationService, secretsStore, initializationProperties, passwordEncoder, typeUtil)

    @Bean
    fun <T> initUsersOnRootKeyInitialized(initialUserService: InitialUsersService<T>): ApplicationListener<RootKeyInitializationReadyEvent<T>> =
        ApplicationListener { evt ->
            initialUserService.initializeUsers(evt.rootKeys)
            publisher.publishEvent(StartupAnnouncementEvent("Initialized Users"))
            publisher.publishEvent(RootKeyUpdatedEvent(evt.rootKeys))
        }
}