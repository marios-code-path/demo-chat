package com.demo.chat.config.deploy.init

import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.InitializingKVStore
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.init.RootKeysSupplier
import com.demo.chat.service.init.RootKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RootKeyInitializationListeners<T>: ApplicationEventPublisherAware {

    @Bean
    @ConditionalOnProperty("app.rootkeys.create", havingValue = "true")
    fun rootKeyGenerator(keyService: IKeyService<T>): RootKeysSupplier<T> =
        RootKeysSupplier(keyService)

    @Bean
    @ConditionalOnProperty("app.rootkeys.create", havingValue = "true")
    fun initializeRootKeys(rootKeys: RootKeys<T>,
                     rootKeyGen: RootKeysSupplier<T>
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            rootKeys.merge(rootKeyGen.get())
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
            println("Root Keys Initialized")
        }

    @Bean
    @ConditionalOnProperty("app.rootkeys.publish.scheme", havingValue = "kv")
    @ConditionalOnBean(RootKeyService::class)
    fun publishRootKeysOnRootKeyInitializedEvent(rootKeyService: RootKeyService): ApplicationListener<RootKeyInitializationReadyEvent<T>> =
        ApplicationListener { evt ->
            rootKeyService.publishRootKeys(evt.rootKeys)
            rootKeyService.rootKeySummary(evt.rootKeys)
        }

    @Bean
    @ConditionalOnProperty("app.rootkeys.publish.scheme", havingValue = "kv")
    @ConditionalOnBean(InitializingKVStore::class)
    fun <T> rootKeysPublishService(
        keyService: IKeyService<T>,
        kvStore: InitializingKVStore,
        mapper: ObjectMapper,
        @Value("\${app.kv.rootkeys}") key: String,
    ) = RootKeyService(kvStore, mapper, key)

    @Bean
    @ConditionalOnProperty("app.rootkeys.consume.scheme", havingValue = "kv")
    @ConditionalOnBean(RootKeyService::class)
    fun mergeRootKeysOnStart(
        rootKeys: RootKeys<T>,
        rootKeyService: RootKeyService
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            rootKeyService.consumeRootKeys(rootKeys)
        }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.publisher = applicationEventPublisher
    }

    lateinit var publisher: ApplicationEventPublisher
}