package com.demo.chat.config.client.discovery

import com.demo.chat.service.client.discovery.LocalhostDiscovery
import com.demo.chat.client.discovery.PropertiesBasedDiscovery
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// TODO This is generic can remove rsocket references.
@Configuration
class ClientDiscoveryConfiguration {

    @Bean
    @ConditionalOnProperty("app.client.discovery", havingValue = "properties")
    fun propertiesBasedDiscovery(clientProps: RSocketClientProperties) = PropertiesBasedDiscovery(clientProps)

    @Bean
    @ConditionalOnProperty("app.client.discovery", havingValue = "local")
    fun localhostDiscovery(
        @Value("\${app.client.discovery.local.host}") host: String,
        @Value("\${app.client.discovery.local.port}") port: Int,
    ) = LocalhostDiscovery(host, port)
}