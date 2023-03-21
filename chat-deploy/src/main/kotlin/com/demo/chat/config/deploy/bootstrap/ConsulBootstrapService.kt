package com.demo.chat.config.deploy.bootstrap

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.ecwid.consul.v1.ConsulClient
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
class ConsulBootstrapService(
    val client: ConsulClient,
    val mapper: ObjectMapper
) {

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "rootkeys")
    fun listenForBootstrapEvent(@Value("app.rootkeys.consul.key") consulKey: String): ApplicationListener<BootstrapEvent> =
        ApplicationListener { event ->
            writeToConsul(event.rootKeys, consulKey)
        }

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "read")
    fun readRootKeysOnStart(
        rootKeys: RootKeys<Any>,
        @Value("app.rootkeys.consul.key") consulKey: String
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            readFromConsul(rootKeys, consulKey)
        }

    fun readFromConsul(rootKeys: RootKeys<Any>, consulKey: String) {
        val kvGet = client.getKVValue(consulKey).value

        Optional.ofNullable(kvGet.value)
            .map {
                mapper.readValue(
                    Base64.getDecoder().decode(it),
                    Map::class.java
                )
            }
            .ifPresent {
                rootKeys.merge(it as Map<String, Key<Any>>)
            }
    }

    fun <T> writeToConsul(rootKeys: RootKeys<T>, consulKey: String) {
        client.setKVValue(consulKey, mapper.writeValueAsString(rootKeys.getMapOfKeyMap()))
    }
}