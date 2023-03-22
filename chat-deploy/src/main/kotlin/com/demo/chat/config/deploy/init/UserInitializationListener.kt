package com.demo.chat.config.deploy.init

import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.bootstrap", havingValue = "init")
class UserInitializationListener<T>(
    private val initialUserService: InitialUsersService<T>,
) : ApplicationEventPublisherAware {

    @Bean
    fun eventListener(): ApplicationListener<RootKeyInitializationReadyEvent<T>> = ApplicationListener { evt ->
        initialUserService.initializeUsers(evt.rootKeys)
        //publisher.publishEvent(UsersInitializedEvent())
    }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.publisher = applicationEventPublisher
    }

    lateinit var publisher: ApplicationEventPublisher
}