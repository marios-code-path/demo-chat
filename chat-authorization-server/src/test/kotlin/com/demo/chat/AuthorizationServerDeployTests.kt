package com.demo.chat

import com.demo.chat.auth.client.RegisteredClientFactory
import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.deploy.authserv.AuthServiceApp
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.secure.service.CoreUserDetailsService
import com.demo.chat.service.client.ClientDiscovery
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.client.ServiceInstance
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import java.util.*

@ExtendWith(SpringExtension::class)
class TempTest {

    private var mapper: ObjectMapper = ObjectMapper().apply {
        registerModules(DefaultChatJacksonModules().allModules())
        registerModule(KotlinModule())
    }

    @MockBean
    private lateinit var repo: RegisteredClientRepository

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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [AuthServiceApp::class, TestConfig::class],
    properties = [
        "spring.config.location=classpath:application.yml",
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=authserv_test",
        "app.rsocket.transport.unprotected", "app.client.rsocket.composite.user",
        "app.client.rsocket.composite.message", "app.client.rsocket.composite.topic",
        "app.service.security.userdetails", "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index"

    ]
)
@Disabled
class AuthorizationServerDeployTests {

    @Autowired
    private lateinit var typeUtil: TypeUtil<Long>

    @Autowired
    private lateinit var coreUserDetailsService: CoreUserDetailsService<Long>

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
class TestConfig {
    @Bean
    fun discovery() = object : ClientDiscovery {

        override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> {
            TODO("Not yet implemented")
        }
    }
}