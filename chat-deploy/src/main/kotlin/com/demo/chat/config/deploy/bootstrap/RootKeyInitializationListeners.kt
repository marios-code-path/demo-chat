package com.demo.chat.config.deploy.bootstrap

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyValueStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@ConditionalOnProperty("spring.cloud.consul.discovery.enabled", havingValue = "true")
@Configuration
class RootKeyInitializationListeners(
    val kvStore: KeyValueStore<String, String>,
    val mapper: ObjectMapper
) {

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "rootkeys")
    fun writeRootKeysOnBootstrap(@Value("app.rootkeys.discovery.key") keyPath: String): ApplicationListener<BootstrapEvent> =
        ApplicationListener { event ->
            writeRootKeys(event.rootKeys, keyPath).subscribe()
        }

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "read")
    fun mergeRootKeysOnStart(
        rootKeys: RootKeys<Any>,
        @Value("app.rootkeys.consul.key") consulKey: String
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            mergeRootKeys(rootKeys, consulKey).subscribe()
        }

    fun mergeRootKeys(rootKeys: RootKeys<Any>, consulKey: String) = kvStore
        .get(Key.funKey(consulKey))
        .doOnNext {
            val map = mapper.readValue(it.data, Map::class.java)
            rootKeys.merge(map as Map<String, Key<Any>>)
        }

    fun writeRootKeys(rootKeys: RootKeys<*>, consulKey: String) = kvStore
        .add(KeyDataPair.create(Key.funKey(consulKey), mapper.writeValueAsString(rootKeys.getMapOfKeyMap())))
}