package com.demo.chat.config.deploy.init

import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.init.RootKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RootKeyInitializationListeners<T>(
    val rootKeyService: RootKeyService<T>
) : ApplicationEventPublisherAware {

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "init")
    fun initializeOnStarted(rootKeys: RootKeys<T>): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { evt ->
            rootKeys.merge(rootKeyService.createDomainKeys())
            rootKeyService.publishRootKeys(rootKeys)
            rootKeyService.rootKeySummary(rootKeys)
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "consume")
    fun mergeRootKeysOnStart(
        rootKeys: RootKeys<T>
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            rootKeyService.consumeRootKeys(rootKeys)
        }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.publisher = applicationEventPublisher
    }

    lateinit var publisher: ApplicationEventPublisher
}


