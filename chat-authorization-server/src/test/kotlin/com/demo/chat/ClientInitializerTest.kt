package com.demo.chat

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import com.demo.chat.deploy.authserv.ClientInitializer
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
import org.springframework.boot.test.mock.mockito.MockBean
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

    @MockBean
    private lateinit var repo: RegisteredClientRepository

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `test runner`() {
        val args = DefaultApplicationArguments("--clientpath=classpath:testclient.json")
        ClientInitializer(repo, mapper).loadClient().run(args)

        val clientCaptor = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repo, Mockito.times(1)).save(clientCaptor.capture())
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
        registerModule(KotlinModule())
    }
}