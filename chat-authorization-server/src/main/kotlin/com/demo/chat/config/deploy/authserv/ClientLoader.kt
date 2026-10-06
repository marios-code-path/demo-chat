package com.demo.chat.config.deploy.authserv

import com.demo.chat.auth.client.RegisteredClientFactory
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties
import org.springframework.context.annotation.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.UrlResource
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.io.File


@Profile("client-init")
@Configuration
@EnableConfigurationProperties(AgentClientProperties::class)
class ClientInitializer(val repo: RegisteredClientRepository,
                        @Qualifier(JACKSON_2_OBJECT_MAPPER) val mapper: ObjectMapper,
                        val agentProps: AgentClientProperties) {

    @Bean
    fun loadOauth2AuthorizationServerProperties(properties: OAuth2AuthorizationServerProperties): ApplicationRunner =
        ApplicationRunner { args ->
            // Every entry registers, not chat-client alone. See CHAT-frcrctdp.
            properties.client.forEach { (name, client) ->
                val clientProps = Oauth2ClientProperties().apply {
                    val reg = client.registration

                    // Boot 4 types these properties as nullable. A registration
                    // with no client id or no secret cannot build a client, so
                    // each failure names the value it missed.
                    val clientId = reg.clientId
                        ?: error("The $name registration carries no client id")

                    this.clientId = clientId
                    this.id = clientId
                    this.additionalScopes = reg.scopes.toList()
                    this.authorizationGrantTypes = reg.authorizationGrantTypes.toList()
                    this.clientAuthenticationMethods = reg.clientAuthenticationMethods.toList()
                    this.redirectUriPrefix = ""
                    this.redirectUris = reg.redirectUris.toList()
                    this.requiresAuthorizationConcent = client.isRequireAuthorizationConsent
                    this.secret = reg.clientSecret
                        ?: error("The $name registration carries no client secret")
                }
                val registered = RegisteredClientFactory(clientProps)()

                val oldClient = repo.findByClientId(registered.clientId)

                if(oldClient == null)
                    repo.save(registered)
            }
        }

    /**
     * Registers one client per agent. See `CHAT-frcrctdp`.
     *
     * It runs before the other runners, so a collision fails the start before
     * any other client is saved.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun registerAgentClients(
        serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>,
        clientProps: Oauth2ClientProperties,
    ): ApplicationRunner = ApplicationRunner { args ->
        val agents = agentProps.requireValid()
        if (agents.isEmpty()) return@ApplicationRunner
        AgentClients.requireNoCollision(
            agents.map { it.clientId },
            mapOf(
                "app.oauth2.client" to listOf(clientProps.clientId),
                "spring.security.oauth2.authorizationserver.client" to bootClientIds(serverProps),
                "--clientpath" to clientPathIds(args),
            ),
        )
        AgentClients.reconcile(repo, agents, agentProps.agentScope!!)
    }

    private fun clientPathIds(args: ApplicationArguments): List<String> =
        args.getOptionValues("clientpath")?.firstOrNull()?.let { listOf(readClientPath(it).clientId) }.orEmpty()

    private fun readClientPath(clientPath: String): Oauth2ClientProperties {
        val resource = if (clientPath.startsWith("classpath:")) {
            ClassPathResource(clientPath.substring(10))
        } else {
            UrlResource(File(clientPath).toURI().toURL())
        }
        return mapper.readValue(resource.inputStream, Oauth2ClientProperties::class.java)
    }

    /**
     * Saves `app.oauth2.client` once, as the memory repository holds it. See
     * `CHAT-uizwrxmf`.
     */
    @Bean
    fun registerAppClient(clientProps: Oauth2ClientProperties): ApplicationRunner =
        ApplicationRunner {
            if (clientProps.clientId.isBlank()) error("app.oauth2.client carries no client id")
            saveClient(clientProps)
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

                saveClient(readClientPath(clientPath))
            }
        }

    fun saveClient(clientProperties: Oauth2ClientProperties) {
        val client = RegisteredClientFactory(clientProperties)()

        val oldClient = repo.findByClientId(client.clientId)

        if(oldClient==null)
            repo.save(client)
    }
}