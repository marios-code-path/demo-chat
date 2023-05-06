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
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.users.create", havingValue = "true")
class UserInitializationListener<T>(val eventPublisher: DeploymentEventPublisher) {

    @Bean
    fun <T> initializeUsersService(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>>,
        secretsStore: SecretsStore<T>,
        initializationProperties: UserInitializationProperties,
        typeUtil: TypeUtil<T>,
    ) = InitialUsersService(userService, authorizationService, secretsStore, initializationProperties, typeUtil)

    @Bean
    fun initUsersOnRootKeyInitialized(initialUserService: InitialUsersService<T>): ApplicationListener<RootKeyInitializationReadyEvent<T>> =
        ApplicationListener { evt ->
            eventPublisher.publishEvent(AvailabilityChangeEvent(evt, ReadinessState.REFUSING_TRAFFIC))
            initialUserService.initializeUsers(evt.rootKeys)
            eventPublisher.publishEvent(StartupAnnouncementEvent("Initialized Users"))
            eventPublisher.publishEvent(RootKeyUpdatedEvent(evt.rootKeys))
            eventPublisher.publishEvent(AvailabilityChangeEvent(evt, ReadinessState.ACCEPTING_TRAFFIC))
        }
}