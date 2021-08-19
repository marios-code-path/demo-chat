package com.demo.chat.test.streams

import com.demo.chat.domain.MessageTopic
import com.demo.chat.streams.app.StreamApp
import com.demo.chat.streams.functions.MessageTopicRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.MessageConverter

class TopicFunctionTests {
    @Test
    fun `should topicRequest turn into Topic`() {
        SpringApplicationBuilder(
            *TestChannelBinderConfiguration.getCompleteConfiguration(
                StreamApp::class.java
            )
        ).web(WebApplicationType.NONE)
            .run("--spring.cloud.function.definition=receiveTopicRequest",
                "--spring.cloud.stream.bindings.receiveTopicRequest-in-0.destination=topic-req",
                "--spring.cloud.stream.bindings.receiveTopicRequest-out-0.destination=topics").use { context ->
                val source = context.getBean(InputDestination::class.java)
                Assertions.assertThat(source).isNotNull
                val userReq = MessageTopicRequest("TEST")
                val converter: MessageConverter =
                    context.getBean(
                        CompositeMessageConverter::class.java
                    )
                val headers: MutableMap<String, Any> = HashMap()
                headers["contentType"] = "application/json"
                val messageHeaders = MessageHeaders(headers)
                val topicCreateMessage = converter.toMessage(userReq, messageHeaders)
                source.send(topicCreateMessage)
                val target = context.getBean(OutputDestination::class.java)
                val messageTopic = target.receive(10000)
                Assertions.assertThat(messageTopic).isNotNull
                val user = converter.fromMessage(messageTopic, MessageTopic::class.java)

                Assertions.assertThat(user).hasFieldOrPropertyWithValue("data", "TEST")
            }
    }
}