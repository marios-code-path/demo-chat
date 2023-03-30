package com.demo.chat.config.client.discovery

import com.demo.chat.client.discovery.ConsulClientDiscovery
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.ecwid.consul.v1.ConsulClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(
    "app.client.discovery",
    havingValue = "consul"
)
@Configuration
class DiscoveryClientConfiguration(val client: ConsulClient, val props: ConsulDiscoveryProperties) {

    @Bean
    @ConditionalOnMissingBean
    fun discoveryClient(): ConsulReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)

    @Bean
    fun consulClientDiscovery(rds: ConsulReactiveDiscoveryClient, configProps: ClientProperties<ClientProperty>) =
        ConsulClientDiscovery(rds, configProps)
}