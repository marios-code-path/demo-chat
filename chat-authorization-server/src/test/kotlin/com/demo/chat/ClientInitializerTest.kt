package com.demo.chat

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.deploy.authserv.AgentClientProperties
import com.demo.chat.config.deploy.authserv.AgentClients
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import com.demo.chat.config.deploy.authserv.ClientInitializer
import com.demo.chat.auth.client.RegisteredClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*


@ExtendWith(SpringExtension::class)
@AutoConfigureJson
@ContextConfiguration(classes = [FooBar::class])
class ClientInitializerTest {

    @MockitoBean
    private lateinit var repo: RegisteredClientRepository

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `test runner`() {
        val args = DefaultApplicationArguments("--clientpath=classpath:testclient.json")
        ClientInitializer(repo, mapper, AgentClientProperties()).loadClient().run(args)

        val clientCaptor = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repo, Mockito.times(1)).save(clientCaptor.capture())
    }


    private fun appClient() = Oauth2ClientProperties().apply {
        clientId = "app-client-id"
        id = "app-client-id"
        secret = "{noop}secret"
        clientAuthenticationMethods = listOf("client_secret_basic")
        authorizationGrantTypes = listOf("client_credentials")
        additionalScopes = listOf("openid")
    }

    @Test
    fun `an absent app client is saved once`() {
        Mockito.`when`(repo.findByClientId("app-client-id")).thenReturn(null)

        ClientInitializer(repo, mapper, AgentClientProperties())
            .registerAppClient(appClient()).run(DefaultApplicationArguments())

        val saved = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repo, Mockito.times(1)).save(saved.capture())
        assertThat(saved.value.clientId).isEqualTo("app-client-id")
        assertThat(saved.value.scopes).containsExactly("openid")
    }

    @Test
    fun `a stored app client is kept`() {
        Mockito.`when`(repo.findByClientId("app-client-id"))
            .thenReturn(RegisteredClientFactory(appClient())())

        ClientInitializer(repo, mapper, AgentClientProperties())
            .registerAppClient(appClient()).run(DefaultApplicationArguments())

        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an app client with no client id fails the start`() {
        assertThatThrownBy {
            ClientInitializer(repo, mapper, AgentClientProperties())
                .registerAppClient(Oauth2ClientProperties()).run(DefaultApplicationArguments())
        }.hasMessage("app.oauth2.client carries no client id")
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    private fun agentProps() = AgentClientProperties().apply {
        agentScope = "chat.mcp"
        agents = listOf(AgentClientProperties.AgentClient().apply { clientId = "client-agent"; username = "Agent" })
    }

    private fun runner(agentProps: AgentClientProperties, vararg args: String) =
        ClientInitializer(repo, mapper, agentProps)
            .registerAgentClients(serverProps(), Oauth2ClientProperties().apply { clientId = "chat-client-id"; id = "chat-client" })
            .run(DefaultApplicationArguments(*args))

    private fun serverProps(): ObjectProvider<OAuth2AuthorizationServerProperties> {
        val props = OAuth2AuthorizationServerProperties().apply {
            client["chat-client"] = OAuth2AuthorizationServerProperties.Client().apply {
                registration.clientId = "chatClient"
            }
        }
        return StaticListableBeanFactory(mapOf("props" to props)).getBeanProvider(OAuth2AuthorizationServerProperties::class.java)
    }

    @Test
    fun `an absent agent client is saved once`() {
        Mockito.`when`(repo.findByClientId("client-agent")).thenReturn(null)

        runner(agentProps())

        val saved = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repo, Mockito.times(1)).save(saved.capture())
        assertThat(AgentClients.differences(saved.value, "chat.mcp")).isEmpty()
    }

    @Test
    fun `a matching agent client is kept`() {
        Mockito.`when`(repo.findByClientId("client-agent"))
            .thenReturn(AgentClients.build("client-agent", "chat.mcp", "kept"))

        runner(agentProps())

        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @ParameterizedTest
    @ValueSource(strings = ["grant", "authentication method", "scope", "consent", "secret prefix", "token format", "token lifetime"])
    fun `a drifted agent row fails the start and names the field`(field: String) {
        val good = AgentClients.build("client-agent", "chat.mcp", "kept")
        val drifted = RegisteredClient.from(good).apply {
            when (field) {
                "grant" -> authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://x")
                "authentication method" -> clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                "scope" -> scope("openid")
                "consent" -> clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                "secret prefix" -> clientSecret("{noop}kept")
                "token format" -> tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                    .accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
                "token lifetime" -> tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                    .accessTokenTimeToLive(Duration.ofMinutes(30)).build())
            }
        }.build()
        Mockito.`when`(repo.findByClientId("client-agent")).thenReturn(drifted)

        assertThatThrownBy { runner(agentProps()) }
            .hasMessageContaining("The stored client 'client-agent' is not an agent client")
            .hasMessageContaining(field)
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an agent id that a boot client uses fails the start`() {
        val props = agentProps().apply { agents.single().clientId = "chatClient" }

        assertThatThrownBy { runner(props) }
            .hasMessage("Agent client id 'chatClient' is also registered by spring.security.oauth2.authorizationserver.client.")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an agent id that the app client row id uses fails the start before a write`(stored: Boolean) {
        if (stored) {
            Mockito.`when`(repo.findById("chat-client"))
                .thenReturn(AgentClients.build("chat-client", "chat.mcp", "kept"))
        }
        val props = agentProps().apply { agents.single().clientId = "chat-client" }

        assertThatThrownBy { runner(props) }
            .hasMessage("Agent client id 'chat-client' is also registered by app.oauth2.client.")
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an agent id that the clientpath row id uses fails the start`() {
        val props = agentProps().apply { agents.single().clientId = "1" }

        assertThatThrownBy { runner(props, "--clientpath=classpath:testclient.json") }
            .hasMessage("Agent client id '1' is also registered by --clientpath.")
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an agent whose row id the store holds for another client fails the start`() {
        Mockito.`when`(repo.findByClientId("client-agent")).thenReturn(null)
        Mockito.`when`(repo.findById("client-agent")).thenReturn(RegisteredClientFactory(appClient().apply { id = "client-agent" })())

        assertThatThrownBy { runner(agentProps()) }
            .hasMessage("Client 'client-agent' needs row id 'client-agent', and the store holds that row for client 'app-client-id'.")
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an app client whose row id the store holds for another client fails the start`() {
        val app = appClient().apply { id = "chat-client" }
        Mockito.`when`(repo.findByClientId("app-client-id")).thenReturn(null)
        Mockito.`when`(repo.findById("chat-client"))
            .thenReturn(AgentClients.build("chat-client", "chat.mcp", "kept"))

        assertThatThrownBy {
            ClientInitializer(repo, mapper, AgentClientProperties())
                .registerAppClient(app).run(DefaultApplicationArguments())
        }.hasMessage("Client 'app-client-id' needs row id 'chat-client', and the store holds that row for client 'chat-client'.")
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an agent id that the clientpath file uses fails the start`() {
        val props = agentProps().apply { agents.single().clientId = "ba89bb6f-8cf9-4b39-8118-2bf917b19bee" }

        assertThatThrownBy { runner(props, "--clientpath=classpath:testclient.json") }
            .hasMessage("Agent client id 'ba89bb6f-8cf9-4b39-8118-2bf917b19bee' is also registered by --clientpath.")
    }

//    private var mapper: ObjectMapper = ObjectMapper().apply {
//        registerModules(DefaultChatJacksonModules().allModules())
//        registerModule(KotlinModule())
//    }

    @Test
    fun shouldGetSomething() {
        val client = Oauth2ClientProperties()
            .apply {
                clientId = UUID.randomUUID().toString()
                id = "1"
                additionalScopes = listOf("user", "topic", "message")
                authorizationGrantTypes = listOf("authorization_code", "refresh_token", "client_credentials")
                redirectUris = listOf(
                    "http://127.0.0.1:8080/login/oauth2/code/chat-client-oidc",
                    "http://127.0.0.1:8080/authorized"
                )
                clientAuthenticationMethods = listOf("client_secret_basic")
                requiresAuthorizationConcent = true
                secret = "{noop}secret"
                redirectUriPrefix = "http://127.0.0.1:8080"
            }

//        val settings = RegisteredClientFactory(client)()
//        val settingsJson = mapper.writeValueAsString(settings)

        val clientJson = mapper.writeValueAsString(client)

        println("CLIENT JSON====" + clientJson)
    }

}

@TestConfiguration
class FooBar {
    @Bean
    fun mapper(): ObjectMapper = ObjectMapper().apply {
        registerModules(DefaultChatJacksonModules().allModules())
        registerModule(KotlinModule.Builder().build())
    }
}