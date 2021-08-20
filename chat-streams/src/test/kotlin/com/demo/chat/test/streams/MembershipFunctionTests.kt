package com.demo.chat.test.streams

import com.demo.chat.domain.TopicMembership
import com.demo.chat.streams.app.StreamApp
import com.demo.chat.streams.functions.TopicMembershipRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.handler.GenericHandler
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.MessagingException
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.MessageConverter

class MembershipFunctionTests {
    @Test
    fun `should membershipRequest turn into Membership`() {
        SpringApplicationBuilder(
            *TestChannelBinderConfiguration.getCompleteConfiguration(
                StreamApp::class.java, ErrorFlows::class.java
            )
        ).web(WebApplicationType.NONE)
            .run("--spring.cloud.function.definition=receiveMembershipRequest",
                "--spring.cloud.stream.bindings.receiveMembershipRequest-in-0.destination=membership-req",
                "--spring.cloud.stream.bindings.receiveMembershipRequest-out-0.destination=membership").use { context ->
                val source: InputDestination = context.getBean(InputDestination::class.java)
                Assertions.assertThat(source).isNotNull
                val membershipReq = TopicMembershipRequest(1L, 2L)
                val errorChan: PublishSubscribeChannel = context.getBean("errorChannel", PublishSubscribeChannel::class.java)
                val converter: MessageConverter =
                    context.getBean(
                        CompositeMessageConverter::class.java
                    )
                val headers: MutableMap<String, Any> = HashMap()
                headers["contentType"] = "application/json"
                val messageHeaders = MessageHeaders(headers)
                val topicCreateMessage = converter.toMessage(membershipReq, messageHeaders)
                source.send(topicCreateMessage)
                // TODO - we must understand why errors are swallowed ( see Json Exception because field 'kId' is being jsonified as 'kid';
                //  I must change the field name for it to work)
                val target = context.getBean(OutputDestination::class.java)
                val messageTopic = target.receive(2000)
                Assertions.assertThat(messageTopic).isNotNull
                val user = converter.fromMessage(messageTopic, TopicMembership::class.java)

                Assertions.assertThat(user).hasFieldOrPropertyWithValue("memberOf", 2L)
                Assertions.assertThat(user).hasFieldOrPropertyWithValue("member", 1L)
            }
    }

    @Configuration
    class ErrorFlows {
        @Bean
        fun errorFlow() =
            IntegrationFlows.from("errorChannel")
                .handle(GenericHandler<MessagingException> { payload, headers ->
                    println("ERRORChannel: " + payload.failedMessage!!.headers)
                    println(payload.localizedMessage)
                    "Error"
                })
                .get()


    }
}