package com.demo.chat.test.rsocket

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.rsocket.server.LocalRSocketServerPort
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringBootTest(classes = [RSocketServerTestConfiguration::class])
open class RequesterTestBase {

    lateinit var requester: RSocketRequester

    @BeforeAll
    internal fun `before all`(
        @Autowired builder: RSocketRequester.Builder,
        @LocalRSocketServerPort port: Int,
    ) {
        requester = builder.tcp("localhost", port)
    }


}