package com.demo.chat.auth.client

import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings

class RegisteredClientFactory(val clientProps: Oauth2ClientProperties) : () -> RegisteredClient {

    override fun invoke(): RegisteredClient {
        val registeredClientBuilder = RegisteredClient.withId(clientProps.id)
            .clientId(clientProps.clientId)
            .clientSecret(clientProps.secret)

        clientProps.clientAuthenticationMethods.forEach { method ->
            registeredClientBuilder.clientAuthenticationMethod(ClientAuthenticationMethod(method))
        }

        clientProps.authorizationGrantTypes.forEach { grant ->
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType(grant))
        }

        clientProps.additionalScopes.forEach { scope ->
            registeredClientBuilder.scope(scope)
        }

        clientProps.redirectUris.forEach {
            registeredClientBuilder.redirectUri(it)
        }

        val registeredClient = registeredClientBuilder
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(clientProps.requiresAuthorizationConcent)
                    .build()
            )
            .build()

        return registeredClient
    }
}