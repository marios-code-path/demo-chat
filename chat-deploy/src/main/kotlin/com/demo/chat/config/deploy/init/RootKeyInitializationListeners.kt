package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.InitializingKVStore
import com.demo.chat.service.init.RootKeysSupplier
import com.demo.chat.service.init.RootKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RootKeyInitializationListeners<T>(val publisher: DeploymentEventPublisher,
    val typeUtil: TypeUtil<T>){

    @Bean
    fun listenForRootKeyInitialized(): ApplicationListener<RootKeyInitializationReadyEvent<T>> =
        ApplicationListener { evt ->
            publisher.publishEvent(StartupAnnouncementEvent(RootKeyService.rootKeySummary(evt.rootKeys)))
        }

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
            publisher.publishEvent(StartupAnnouncementEvent("Root Keys Initialized"))
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }

    @Bean
    @ConditionalOnProperty("app.rootkeys.publish.scheme", havingValue = "kv")
    fun publishRootKeysOnRootKeyInitializedEvent(rootKeyService: RootKeyService<T>): ApplicationListener<RootKeyInitializationReadyEvent<T>> =
        ApplicationListener { evt ->
            rootKeyService.publishRootKeys(evt.rootKeys)
            publisher.publishEvent(StartupAnnouncementEvent("Root Keys Published"))
        }

    @Bean
    @ConditionalOnProperty("app.kv.rootkeys")
    fun rootKeysService(
        keyService: IKeyService<T>,
        kvStore: InitializingKVStore,
        @Value("\${app.kv.rootkeys}") key: String,
    ) = RootKeyService(kvStore, typeUtil, key)

    @Bean
    @ConditionalOnProperty("app.rootkeys.consume.scheme", havingValue = "kv")
    fun mergeRootKeysOnStart(
        rootKeys: RootKeys<T>,
        rootKeyService: RootKeyService<T>
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            rootKeyService.consumeRootKeys(rootKeys)
        }
}