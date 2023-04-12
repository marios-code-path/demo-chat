package com.demo.chat.config.deploy.authserv

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import java.util.*


@Configuration
class ClientRepositoryConfiguration {

    @Value("\${app.oauth2.client.key}")
    private lateinit var clientKey: String

    @Value("\${app.oauth2.client.id}")
    private lateinit var clientId: String

    @Value("\${app.oauth2.client.redirect-uri-prefix}")
    private lateinit var redirectUriPrefix: String

    @Value("\${app.oauth2.client.secret}")
    private lateinit var clientSecret: String

    @Bean
    fun registeredClientRepository(clientProps: Oauth2ClientProperties): RegisteredClientRepository {
        val registeredClientBuilder = RegisteredClient.withId(clientKey)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("${redirectUriPrefix}/login/oauth2/code/chat-client-oidc")
            .redirectUri("${redirectUriPrefix}/authorized")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)

        clientProps.additionalScopes.forEach { scope ->
            registeredClientBuilder.scope(scope)
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