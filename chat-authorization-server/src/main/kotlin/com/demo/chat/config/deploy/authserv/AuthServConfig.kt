package com.demo.chat.config.deploy.authserv

import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.authorization.*
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*


@Configuration
class AuthServConfig(
    @Value("\${app.oauth2.jwk.path}") val resource: Resource,
    @Value("\${app.oauth2.entrypoint-path}") val entrypointPath: String
) {

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

    //@Bean
    fun jwkSetSourceRSA(): JWKSource<SecurityContext> {
        val keyPair = generateRsaKey()
        val publicKey = keyPair.getPublic() as RSAPublicKey
        val privateKey = keyPair.getPrivate() as RSAPrivateKey
        val rsaKey = RSAKey
            .Builder(publicKey)
            .privateKey(privateKey)
            .keyID("1")//UUID.randomUUID().toString())
            .build()

        val jwkSet = JWKSet(rsaKey)
        return ImmutableJWKSet(jwkSet)
    }

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

    //@Bean
    fun jwkSet(
        @Value("\${keycert}") keyCert: Resource,
        @Value("111111") storePass: String
    ): JWKSource<SecurityContext> {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            this.load(keyCert.inputStream, storePass.toCharArray())
        }
        val key = keyStore.getKey("localhost", storePass.toCharArray())
        val cert = keyStore.getCertificate("localhost") as X509Certificate
        val ecJWK = com.nimbusds.jose.jwk.ECKey.parse(cert)
        val jwk = com.nimbusds.jose.jwk.ECKey.Builder(ecJWK)
            .keyUse(KeyUse.SIGNATURE)
            .keyID("1")
            .privateKey(key as ECPrivateKey)
            .keyStore(keyStore)
            .build()
        val jwkSet = JWKSet(jwk)

        return ImmutableJWKSet(jwkSet)
    }


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