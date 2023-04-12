package com.demo.chat.config.deploy.authserv

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.*
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*

@Configuration
class AuthServConfig() {

    @Value("\${app.oauth2.entrypoint-path}")
    private lateinit var entrypointPath: String

    @Throws(Exception::class)
    @Bean
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)
        http.getConfigurer(OAuth2AuthorizationServerConfigurer::class.java)
            .oidc(Customizer.withDefaults())

        http.exceptionHandling { exceptions ->
            exceptions
                .authenticationEntryPoint(
                    LoginUrlAuthenticationEntryPoint(entrypointPath)
                )
        }
            .oauth2ResourceServer { it.jwt() }

        return http.build()
    }

    @Value("\${app.oauth2.jwk.path}")
    private lateinit var resource: String

    @Bean
    fun jwkSetSource(): JWKSource<SecurityContext> {
        //val jwkContent: String = resource.inputStream.bufferedReader().use { it.readText() }
        val jwkContent: String = Files.readAllBytes(Paths.get(resource)).toString(Charsets.UTF_8)
        val jwk = JWK.parse(jwkContent)
        val jwkSet = JWKSet(jwk)

        return ImmutableJWKSet(jwkSet)
    }
//
//    @Bean
//    fun jwkSource(): JWKSource<SecurityContext> {
//        val keyPair = generateRsaKey()
//        val publicKey = keyPair.getPublic() as RSAPublicKey
//        val privateKey = keyPair.getPrivate() as RSAPrivateKey
//        val rsaKey = RSAKey.Builder(publicKey)
//            .privateKey(privateKey)
//            .keyID(UUID.randomUUID().toString())
//            .build()
//
//        val jwkSet = JWKSet(rsaKey)
//        return ImmutableJWKSet(jwkSet)
//    }

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)

    @Bean
    fun oauth2AuthorizationService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationService {
        return InMemoryOAuth2AuthorizationService()
    }

    @Bean
    fun oauth2AuthorizationConsentService(registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationConsentService {
        return InMemoryOAuth2AuthorizationConsentService()
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder().build()

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun generateRsaKey(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            return keyPairGenerator.generateKeyPair()
        }
    }
}