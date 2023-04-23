package com.demo.chat.config.deploy.authserv

import com.nimbusds.jose.JWSAlgorithm
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm
import org.springframework.security.oauth2.jose.jws.JwsAlgorithms
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings


@Configuration
class ClientRepositoryConfiguration {

    @Bean
    fun registeredClientRepository(clientProps: Oauth2ClientProperties): RegisteredClientRepository {
        val registeredClientBuilder = RegisteredClient.withId(clientProps.key)
            .clientId(clientProps.id)
            .clientSecret(clientProps.secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)

        clientProps.additionalScopes.forEach { scope ->
            registeredClientBuilder.scope(scope)
        }

        clientProps.redirectUris.forEach {
            registeredClientBuilder.redirectUri(it)
        }

        val registeredClient = registeredClientBuilder
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireAuthorizationConsent(true)
                    .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.ES256)
                    .build()
            )
            .tokenSettings(
                TokenSettings.builder()
                    .idTokenSignatureAlgorithm(SignatureAlgorithm.ES256)
                    .build()
            )
            .build()

        return InMemoryRegisteredClientRepository(registeredClient)
    }
}