package com.demo.chat.config.deploy.authserv

import com.demo.chat.auth.client.RegisteredClientFactory
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@Configuration(proxyBeanMethods = false)
@ComponentScan("com.demo.chat.config.client.discovery", excludeFilters = [])
@EnableConfigurationProperties(Oauth2ClientProperties::class)
@EnableWebMvc
class AuthorizationServerConfig(@Value("\${app.oauth2.jwk.path}") val resource: Resource) {

    @Bean
    fun jwtCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> {
        return OAuth2TokenCustomizer { context ->
            context.jwsHeader.algorithm(SignatureAlgorithm.ES256)
        }
    }

    @Bean
    fun jwkSetSource(): JWKSource<SecurityContext> {
        val jwkContent: String = resource
            .inputStream
            .bufferedReader().use { it.readText() }
        val jwk = JWK.parse(jwkContent)
        val jwkSet = JWKSet(jwk)

        return ImmutableJWKSet(jwkSet)
    }

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)

    @Profile("memory")
    @Bean
    fun registeredClientRepo(clientProps: Oauth2ClientProperties): RegisteredClientRepository =
        InMemoryRegisteredClientRepository(
            RegisteredClientFactory(clientProps)()
        )

    @Profile("memory")
    @Bean
    fun oauth2AuthorizationService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationService {
        return InMemoryOAuth2AuthorizationService()
    }

    @Profile("memory")
    @Bean
    fun oauth2AuthorizationConsentService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationConsentService {
        return InMemoryOAuth2AuthorizationConsentService()
    }

    @Profile("jdbc")
    @Bean
    fun registeredClientJdbcRepo(
        clientProps: Oauth2ClientProperties,
        template: JdbcTemplate
    ) = JdbcRegisteredClientRepository(template)

    @Profile("jdbc")
    @Bean
    fun oauth2AuthorizationServiceJdbc(
        registeredClientRepository: RegisteredClientRepository,
        template: JdbcTemplate
    ): OAuth2AuthorizationService = JdbcOAuth2AuthorizationService(template, registeredClientRepository)


    @Profile("jdbc")
    @Bean
    fun oauth2AuthorizationConsentServiceJdbc(
        registeredClientRepository: RegisteredClientRepository,
        template: JdbcTemplate
    ): OAuth2AuthorizationConsentService = JdbcOAuth2AuthorizationConsentService(template, registeredClientRepository)


    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder().build()
}