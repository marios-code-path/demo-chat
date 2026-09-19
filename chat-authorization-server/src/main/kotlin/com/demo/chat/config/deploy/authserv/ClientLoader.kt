package com.demo.chat.config.deploy.authserv

import com.demo.chat.auth.client.RegisteredClientFactory
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties
import org.springframework.context.annotation.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.UrlResource
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.io.File


@Profile("client-init")
@Configuration
class ClientInitializer(val repo: RegisteredClientRepository,
                        @Qualifier(JACKSON_2_OBJECT_MAPPER) val mapper: ObjectMapper) {

    @Bean
    fun loadOauth2AuthorizationServerProperties(properties: OAuth2AuthorizationServerProperties): ApplicationRunner =
        ApplicationRunner { args ->
            val clientProps = Oauth2ClientProperties().apply {
                val client = properties.client["chat-client"]!!
                val reg = client.registration

                // Boot 4 types these properties as nullable. A registration
                // with no client id or no secret cannot build a client, so
                // each failure names the value it missed.
                val clientId = reg.clientId
                    ?: error("The chat-client registration carries no client id")

                this.clientId = clientId
                this.id = clientId
                this.additionalScopes = reg.scopes.toList()
                this.authorizationGrantTypes = reg.authorizationGrantTypes.toList()
                this.clientAuthenticationMethods = reg.clientAuthenticationMethods.toList()
                this.redirectUriPrefix = ""
                this.redirectUris = reg.redirectUris.toList()
                this.requiresAuthorizationConcent = client.isRequireAuthorizationConsent
                this.secret = reg.clientSecret
                    ?: error("The chat-client registration carries no client secret")
            }
            val client = RegisteredClientFactory(clientProps)()

            val oldClient = repo.findByClientId(client.clientId)

            if(oldClient == null)
                repo.save(client)
        }

    @Bean
    fun loadClient(): ApplicationRunner =
        ApplicationRunner { args ->
            if(args.containsOption("clientpath")) {
                // Boot 4 types getOptionValues as nullable. The option is
                // present, because containsOption answered true, so an
                // absent value is a contract breach and it is named.
                val clientPath = args.getOptionValues("clientpath")
                    ?.firstOrNull()
                    ?: error("The clientpath option carries no value")

                val resource = if(clientPath.startsWith("classpath:")) {
                    ClassPathResource(clientPath.substring(10))
                } else {
                    UrlResource(File(clientPath).toURI().toURL())
                }

                val clientProps = mapper.readValue(resource.inputStream, Oauth2ClientProperties::class.java)
                saveClient(clientProps)
            }
        }

    fun saveClient(clientProperties: Oauth2ClientProperties) {
        val client = RegisteredClientFactory(clientProperties)()

        val oldClient = repo.findByClientId(client.clientId)

        if(oldClient==null)
            repo.save(client)
    }
}