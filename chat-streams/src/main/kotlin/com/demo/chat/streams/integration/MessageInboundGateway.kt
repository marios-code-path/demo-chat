package com.demo.chat.streams.integration

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.IKeyService
import com.demo.chat.service.SubscriptionDirectory
import com.demo.chat.streams.functions.MessageSendRequest
import com.demo.chat.streams.functions.TopicMembershipRequest
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.webflux.dsl.WebFlux
import org.springframework.integration.webflux.dsl.WebFluxInboundEndpointSpec
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.SubscribableChannel
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class MessageInboundGateway<T>(val keyService: IKeyService<T>) {

    @Bean("message.broadcast")
    fun messageBroadcastChannel(): SubscribableChannel = PublishSubscribeChannel()

    val channels: MutableMap<T, PublishSubscribeChannel> = ConcurrentHashMap()

    val inboxes: MutableMap<T, MessageChannel> = ConcurrentHashMap()

    @Bean
    fun chatMessageInboundFlowSpec(): WebFluxInboundEndpointSpec = WebFlux
        .inboundGateway("/message")
        .requestMapping { inbound -> inbound.methods(HttpMethod.POST) }
        .requestPayloadType(MessageSendRequest::class.java)
        .requestChannel("messages-in")
        .extractReplyPayload(true)
        .replyChannel("messages-out")

    @Bean
    fun chatMessageTransformRouter() = integrationFlow("messages-in") {
        // TODO: refactor into more compact'ish form.
        transform<MessageSendRequest<T, T>> { req ->
            val msgId = keyService.key(Message::class.java)
            Message.create(MessageKey.create(msgId, req.from, req.dest), req.msg, true)

            channel(channels[req.dest]!!)
        }
    }

    @Bean
    fun topicSubscriptionFlowSpec() = WebFlux
        .inboundGateway("/topics")
        .requestMapping { inbound -> inbound.methods(HttpMethod.POST) }
        .requestPayloadType(TopicMembershipRequest::class.java)
        .requestChannel("topic-in")

    @ServiceActivator(requiresReply = "false", async = "true", inputChannel = "topic-in")
    fun topicSubscriber(req: TopicMembershipRequest<T>): Mono<Void> = Mono.just(req)
        .doOnNext {
            if (!inboxes.containsKey(req.principal))
                inboxes[req.principal] = QueueChannel(0)

            if (!channels.containsKey(req.destination))
                channels[req.destination] = PublishSubscribeChannel()

            // channels[req.destination].subscribe ( inbox[req.principal] )
        }
        .then()
}