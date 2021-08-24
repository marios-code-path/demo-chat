package com.demo.chat.test.streams

import com.demo.chat.domain.User
import com.demo.chat.streams.functions.UserCreateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.MessageConverter

class UserFunctionsTests {

    @Test
    fun `should userRequest turn into User`() {
        SpringApplicationBuilder(
            *TestChannelBinderConfiguration.getCompleteConfiguration(
                TestStreamConfiguration::class.java
            )
        ).web(WebApplicationType.NONE)
            .run("--spring.cloud.function.definition=receiveUserRequest",
                "--spring.cloud.stream.bindings.receiveUserRequest-in-0.destination=user-req",
                "--spring.cloud.stream.bindings.receiveUserRequest-out-0.destination=users").use { context ->
                val source = context.getBean(InputDestination::class.java)

                val userReq = UserCreateRequest("user1","handle1","http://localhost")
                val converter: MessageConverter =
                    context.getBean(
                        CompositeMessageConverter::class.java
                    )
                val headers: MutableMap<String, Any> = HashMap()
                headers["contentType"] = "application/json"
                val messageHeaders = MessageHeaders(headers)
                val userRequestMessage = converter.toMessage(userReq, messageHeaders)
                source.send(userRequestMessage)
                val target = context.getBean(OutputDestination::class.java)
                val userMessage = target.receive(10000)
                assertThat(userMessage).isNotNull
                val user = converter.fromMessage(userMessage, User::class.java)

                assertThat(user).hasFieldOrPropertyWithValue("name", "user1")
                assertThat(user).hasFieldOrPropertyWithValue("handle", "handle1")
            }
    }
}