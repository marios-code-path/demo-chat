package com.demo.chat.config.agent

import com.demo.chat.service.composite.ChatUserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

/**
 * One place that turns on the `app.security` binding. A reader finds the
 * required property set from here. See `CHAT-pgpmsgvr`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentSecurityProperties::class)
class AgentSecurityConfiguration {

    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.jwt.jwkPath)

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity()

    @Bean
    @ConditionalOnBean(ChatUserService::class)
    fun <T> agentIdentityLifecycle(
        users: ChatUserService<T>,
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> =
        AgentIdentityLifecycle(users, identity, properties)
}
