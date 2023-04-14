package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.domain.knownkey.GenerateRootKeyInitializer
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Deprecated("Use RootKeyInitializerListeners... instead")
class GenerateRootKeyInitializerListener<T>(
    val rootKeys: RootKeys<T>,
    val keyGenerator: IKeyGenerator<T>,
    val publisher: DeploymentEventPublisher
) {

    @EventListener
    fun onStartup(event: ApplicationStartedEvent) {
        GenerateRootKeyInitializer(keyGenerator).initRootKeys(rootKeys)
        publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
    }
}