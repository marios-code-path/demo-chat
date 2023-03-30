package com.demo.chat.config.client.discovery

import com.demo.chat.client.discovery.LocalhostDiscovery
import com.demo.chat.client.discovery.PropertiesBasedDiscovery
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClientDiscoveryConfiguration {

    @Bean
    @ConditionalOnProperty("app.client.discovery", havingValue = "default")
    fun propertiesBasedDiscovery(clientProps: RSocketClientProperties) = PropertiesBasedDiscovery(clientProps)

    @Bean
    @ConditionalOnProperty("app.client.discovery", havingValue = "local")
    fun localhostDiscovery() = LocalhostDiscovery()
}