package com.demo.chat.auth.client

import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings


// This class takes our own properties and maps them to RegisteredClient's.
// it is used instead of
// org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerPropertiesMapper
// because it is private.
//
// TODO:::
// We can deprecate this once we are sure of the final work flow from clean server to one with admin client, and
// many user clients. Right now, this is used to load initial clients into JDBC.
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