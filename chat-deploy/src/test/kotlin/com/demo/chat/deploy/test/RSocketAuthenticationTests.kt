package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.security.RSocketServerConfiguration
import com.demo.chat.test.rsocket.TestConfigurationRSocketServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    initializers = [RSocketPortInfoApplicationContextInitializer::class],
    classes = [
        RSocketSecurityAutoConfiguration::class,
        RSocketServerConfiguration::class,
    ]
)
class RSocketAuthenticationTests : TestConfigurationRSocketServer(false) {

    @Test
    fun `should route credentials to test endpoint`(requester: RSocketRequester) {
        val client = requester
        client
                .route("auth")
                .data(UUID.randomUUID().toString())
                .retrieveMono(String::class.java)
                .subscribe { println(it) }
    }
}

@Controller
class RSocketAuthenticationTestController{

    @MessageMapping("test")
    fun test(message: String): String {
        println("RECEIVED: $message")
        return message
    }
}