package com.demo.chat.config.deploy.authserv

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration
@EnableWebSecurity
class DefaultSecurityConfig(
    @Value("\${app.oauth2.entrypoint-path:localhost/login}") val entrypointPath: String
) {

    @Bean
    @Order(2)
    @Throws(Exception::class)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorizeRequests ->
                authorizeRequests
                    .requestMatchers(EndpointRequest.to("health")).permitAll()
                    .anyRequest().authenticated()
            }
            .formLogin(Customizer.withDefaults())

        return http.build()
    }

    @Bean fun sessionRegistry() = SessionRegistryImpl()

    @Bean fun httpSessionEvenPublisher() = HttpSessionEventPublisher()

    /** A deployed server keeps the replay record. See `CHAT-wdvjqtpb`. */
    @Bean
    @Profile("jdbc")
    fun jdbcAssertionReplayStore(
        template: JdbcTemplate,
        agentProperties: AgentClientProperties,
    ): AssertionReplayStore =
        // Only a private-key agent needs the table, so only that deployment
        // must hold it before the first start.
        JdbcAssertionReplayStore(template, requireTable = agentProperties.agents.any { it.jwkSetUri != null })

    /** Without a database the record lives in the process. */
    @Bean
    @Profile("!jdbc")
    fun memoryAssertionReplayStore(): AssertionReplayStore = AssertionReplayCache()

    @Throws(Exception::class)
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        replayStore: AssertionReplayStore,
    ): SecurityFilterChain {
        val assertionDecoders = ReplayRejectingDecoderFactory(replayStore)
        // Security 7 removed OAuth2AuthorizationServerConfiguration
        // .applyDefaultSecurity, and it offers no static replacement. The
        // three calls below are what that method did, read from the
        // bytecode of Authorization Server 1.5.8:
        //   1. take the endpoints matcher of a fresh configurer
        //   2. apply the configurer with the default customizer
        //   3. require authentication on any request it matches
        // The old class also offered a static authorizationServer() factory.
        // The 7.0.7 class does not, so the configurer is constructed.
        // oidc is enabled before the matcher is read. getEndpointsMatcher
        // resolves lazily, so this matches the earlier order of effect.
        val authorizationServer = OAuth2AuthorizationServerConfigurer()
            .oidc(Customizer.withDefaults())
            .clientAuthentication { clientAuthentication ->
                // The default provider verifies the signature. Swap its
                // decoder for one that also refuses a repeated jti.
                clientAuthentication.authenticationProviders { providers ->
                    providers.filterIsInstance<JwtClientAssertionAuthenticationProvider>()
                        .forEach { it.setJwtDecoderFactory(assertionDecoders) }
                }
            }

        http.securityMatcher(authorizationServer.endpointsMatcher)
            .with(authorizationServer, Customizer.withDefaults())
            .authorizeHttpRequests { requests -> requests.anyRequest().authenticated() }

        http.exceptionHandling { exceptions ->
            exceptions
                .authenticationEntryPoint(
                    LoginUrlAuthenticationEntryPoint(entrypointPath)
                )
        }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }

        return http.build()
    }
}
