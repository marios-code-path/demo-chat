package com.demo.chat.config.deploy.init.runners

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyUpdatedEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.init.InitialUsersService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

//@Component
//@ConditionalOnProperty("app.users.create", havingValue = "true")
//@Order(20)
class InitOnRootKeyInitialized<T>(
    val eventPublisher: DeploymentEventPublisher,
    val initialUserService: InitialUsersService<T>,
    val rootKeys: RootKeys<T>
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        initialUserService.initializeUsers(rootKeys)
        eventPublisher.publishEvent(StartupAnnouncementEvent("Initialized Users"))
        eventPublisher.publishEvent(RootKeyUpdatedEvent(rootKeys))
    }

}