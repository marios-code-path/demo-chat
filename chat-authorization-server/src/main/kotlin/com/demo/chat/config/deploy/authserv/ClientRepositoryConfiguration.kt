package com.demo.chat.config.deploy.authserv

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings


@Configuration
class ClientRepositoryConfiguration {

    @Bean
    fun registeredClientRepository(clientProps: Oauth2ClientProperties): RegisteredClientRepository {
        val registeredClientBuilder = RegisteredClient.withId(clientProps.key)
            .clientId(clientProps.id)
            .clientSecret(clientProps.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)

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
                    .requireAuthorizationConsent(false)
                    .build()
            )
            .build()

        return InMemoryRegisteredClientRepository(registeredClient)
    }
}