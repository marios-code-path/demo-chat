package com.demo.chat.config.rsocket

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentIdentityLifecycle
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.service.composite.ChatUserService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import reactor.core.publisher.Mono

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
@EnableConfigurationProperties(AgentSecurityProperties::class)
class RSocketAgentSecurityConfiguration<T> {

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity()

    @Bean
    fun validateAgentSecurityProperties(properties: AgentSecurityProperties) =
        AgentSecurityPropertiesValidator(properties)

    @Bean
    @ConditionalOnProperty("app.security.agent.username")
    fun agentIdentityLifecycle(
        @Qualifier("userService") users: ChatUserService<T>,
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(users, identity, properties)

    @Bean
    @ConditionalOnProperty("app.security.jwt.jwk-path")
    fun agentReactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.requireComplete().jwt.jwkPath)

    @Bean
    fun rsocketAuthenticationManager(
        users: ReactiveUserDetailsService,
        passwordEncoder: PasswordEncoder,
        properties: AgentSecurityProperties,
        decoder: ObjectProvider<ReactiveJwtDecoder>,
        identity: AgentIdentity,
    ): ReactiveAuthenticationManager {
        val simple = UserDetailsRepositoryReactiveAuthenticationManager(users).apply {
            setPasswordEncoder(passwordEncoder)
        }
        val bearer = if (!properties.isConfigured()) {
            BearerAuthenticationNotConfiguredManager()
        } else {
            val jwtDecoder = decoder.getIfAvailable {
                throw IllegalStateException("The configured agent bearer path has no JWT decoder.")
            }
            JwtReactiveAuthenticationManager(jwtDecoder).apply {
                setJwtAuthenticationConverter(
                    AgentAuthenticationConverter(identity, properties.requireComplete().agent.clientId)
                )
            }
        }
        return RSocketAuthenticationManager(simple, bearer)
    }
}

class RSocketAuthenticationManager(
    private val simple: ReactiveAuthenticationManager,
    private val bearer: ReactiveAuthenticationManager,
) : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> = when (authentication) {
        is BearerTokenAuthenticationToken -> bearer.authenticate(authentication)
        else -> simple.authenticate(authentication)
    }
}

class BearerAuthenticationNotConfiguredManager : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> = Mono.error(
        org.springframework.security.authentication.BadCredentialsException(
            "Bearer authentication is not configured for this core."
        )
    )
}

class AgentSecurityPropertiesValidator(properties: AgentSecurityProperties) {

    init {
        if (properties.isConfigured()) {
            properties.requireComplete()
        }
    }
}
