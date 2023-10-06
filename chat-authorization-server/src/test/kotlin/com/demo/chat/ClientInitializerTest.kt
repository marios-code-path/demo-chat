package com.demo.chat

import com.demo.chat.config.DefaultChatJacksonModules
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
}

@TestConfiguration
class FooBar {
    @Bean
    fun mapper(): ObjectMapper = ObjectMapper().apply {
        registerModules(DefaultChatJacksonModules().allModules())
        registerModule(KotlinModule())
    }
}