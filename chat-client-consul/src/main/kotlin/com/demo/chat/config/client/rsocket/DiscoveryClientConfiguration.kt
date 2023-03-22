package com.demo.chat.config.client.rsocket

import com.ecwid.consul.v1.ConsulClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(
    "app.rsocket.client.requester.factory",
    havingValue = "consul"
)
@Configuration
class DiscoveryClientConfiguration(val client: ConsulClient, val props: ConsulDiscoveryProperties) {
    @Bean
    @ConditionalOnMissingBean
    fun discoveryClient(): ConsulReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)
}