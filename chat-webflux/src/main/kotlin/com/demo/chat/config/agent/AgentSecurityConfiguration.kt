package com.demo.chat.config.agent

import com.demo.chat.config.CompositeServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

/**
 * One place that turns on the `app.security` binding. A reader finds the
 * required property set from here. See `CHAT-pgpmsgvr`.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app", name = ["primary"], havingValue = "REST")
@EnableConfigurationProperties(AgentSecurityProperties::class)
class AgentSecurityConfiguration {

    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.requireComplete().jwt.jwkPath)

    @Bean
    fun agentAuthenticationConverter(
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        AgentAuthenticationConverter(identity, properties.requireComplete().agent.clientId)

    @Bean
    fun agentResourceServerChain(
        properties: AgentSecurityProperties,
        decoder: ReactiveJwtDecoder,
        converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
    ): AgentResourceServerChain {
        properties.requireComplete()
        return AgentResourceServerChain(properties, decoder, converter)
    }

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity()

    @Bean
    fun restRSocketAuthenticationManager(): ReactiveAuthenticationManager =
        ReactiveAuthenticationManager {
            Mono.error(BadCredentialsException("RSocket authentication is unavailable on the REST facade."))
        }

    @Bean
    fun <T> agentIdentityLifecycle(
        services: CompositeServiceBeans<T, String>,
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(services.userService(), identity, properties)
}
