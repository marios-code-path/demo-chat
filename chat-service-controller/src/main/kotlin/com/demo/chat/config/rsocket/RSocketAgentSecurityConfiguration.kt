package com.demo.chat.config.rsocket

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentIdentities
import com.demo.chat.config.agent.AgentIdentityLifecycle
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.config.agent.AgentSecurityPropertiesGuard
import org.springframework.core.env.ConfigurableEnvironment
import com.demo.chat.service.composite.ChatUserService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.security.access.AccessDeniedException
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
    fun agentIdentities(): AgentIdentities = AgentIdentities()

    @Bean
    fun validateAgentSecurityProperties(
        properties: AgentSecurityProperties,
        environment: ConfigurableEnvironment,
    ) = AgentSecurityPropertiesValidator(properties, environment)

    /** It does nothing when the core holds no agent value. See `CHAT-frcrctdp`. */
    @Bean
    fun agentIdentityLifecycle(
        @Qualifier("userService") users: ChatUserService<T>,
        identities: AgentIdentities,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(users, identities, properties)

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
        identities: AgentIdentities,
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
            val complete = properties.requireComplete()
            RequiredScopeAuthenticationManager(
                JwtReactiveAuthenticationManager(jwtDecoder).apply {
                    setJwtAuthenticationConverter(AgentAuthenticationConverter(identities))
                },
                complete.requiredAuthority(),
            )
        }
        return RSocketAuthenticationManager(simple, bearer)
    }
}

/**
 * Requires the configured scope on an authenticated agent token.
 *
 * The REST chain requires the same authority through `hasAuthority`. A token
 * with no such scope is an authorization refusal on both paths. REST answers
 * 403, and the core answers the AUTHORIZATION envelope. See CHAT-mpjtnpqv.
 */
class RequiredScopeAuthenticationManager(
    private val delegate: ReactiveAuthenticationManager,
    private val requiredAuthority: String,
) : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> =
        delegate.authenticate(authentication).flatMap { authenticated ->
            if (authenticated.authorities.any { it.authority == requiredAuthority }) {
                Mono.just(authenticated)
            } else {
                Mono.error(AccessDeniedException("The token does not carry the required scope."))
            }
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

class AgentSecurityPropertiesValidator(properties: AgentSecurityProperties, environment: ConfigurableEnvironment) {

    init {
        AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment)
        if (properties.isConfigured()) {
            properties.requireComplete()
        }
    }
}
