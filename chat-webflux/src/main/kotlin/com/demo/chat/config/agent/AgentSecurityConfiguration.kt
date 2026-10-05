package com.demo.chat.config.agent

import com.demo.chat.config.CompositeServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
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
        identities: AgentIdentities,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> = AgentAuthenticationConverter(identities)

    /**
     * **A REST launch refuses the core REST controllers here.** Every REST
     * launch builds this chain, because `WebFluxSecurity` requires it. See
     * [CoreRestControllers] and `CHAT-bnnkhgbd`.
     */
    @Bean
    fun agentResourceServerChain(
        properties: AgentSecurityProperties,
        decoder: ReactiveJwtDecoder,
        converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
        environment: ConfigurableEnvironment,
    ): AgentResourceServerChain {
        AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment)
        CoreRestControllers.requireAbsent(environment)
        properties.requireComplete()
        return AgentResourceServerChain(properties, decoder, converter)
    }

    @Bean
    fun agentIdentities(): AgentIdentities = AgentIdentities()

    @Bean
    fun restRSocketAuthenticationManager(): ReactiveAuthenticationManager =
        ReactiveAuthenticationManager {
            Mono.error(BadCredentialsException("RSocket authentication is unavailable on the REST facade."))
        }

    @Bean
    fun <T> agentIdentityLifecycle(
        services: CompositeServiceBeans<T, String>,
        identities: AgentIdentities,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(services.userService(), identities, properties)
}
