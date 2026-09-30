package com.demo.chat.config.agent

import com.demo.chat.config.CompositeServiceBeans
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * One place that turns on the `app.security` binding. A reader finds the
 * required property set from here. See `CHAT-pgpmsgvr`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentSecurityProperties::class)
class AgentSecurityConfiguration {

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity()

    @Bean
    fun <T> agentIdentityLifecycle(
        compositeServices: CompositeServiceBeans<T, String>,
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> =
        AgentIdentityLifecycle(compositeServices.userService(), identity, properties)
}
