package com.demo.chat.test.streams

import com.demo.chat.domain.MessageTopic
import com.demo.chat.streams.core.MessageTopicRequest
import com.demo.chat.streams.core.StreamApp
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

class TopicRequestStreamTests {
    @Test
    fun `should topicRequest turn into Topic`() {
        SpringApplicationBuilder(
            *TestChannelBinderConfiguration.getCompleteConfiguration(
                StreamApp::class.java
            )
        ).web(WebApplicationType.NONE)
            .run().use { context ->
                val source = context.getBean(InputDestination::class.java)
                val userReq = MessageTopicRequest("TEST")
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
                val messageTopic = target.receive(10000)
                Assertions.assertThat(messageTopic).isNotNull
                val user = converter.fromMessage(messageTopic, MessageTopic::class.java)

                Assertions.assertThat(user).hasFieldOrPropertyWithValue("name", "TEST")
            }
    }
}