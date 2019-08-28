package com.demo.chatgateway

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chatevents.service.TopicServiceMemory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.*

//@ConfigurationProperties("ws-gateway-config")
class WebSocketConfigurationProperties(var port: Int) {
    constructor() : this(0) {}
}


@Configuration
class WebSocketConfiguration() {

    @Bean
    fun reactiveServerFactory(): ReactiveWebServerFactory = NettyReactiveWebServerFactory(websocketConfiguration().port)

    @Bean
    fun websocketConfiguration() = WebSocketConfigurationProperties(9090)

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }

    @Bean
    fun urlMapping(): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.urlMap = mapOf(Pair("/dist", webSocketHandler()))
        simpleMapping.order = 10
        simpleMapping.setCorsConfigurations(mapOf(Pair("*", CorsConfiguration().applyPermitDefaultValues())))
        return simpleMapping
    }

    @Bean
    fun webSocketHandler(): WebSocketHandler = WebSocketHandler { session ->
        session.send(
                Flux.interval(Duration.ofSeconds(1))
                        .map { n -> n!!.toString() }
                        .map<WebSocketMessage> { session.textMessage(it) })
                .and(session.receive()
                        .map { it.payloadAsText }
                )
    }

}

@Configuration
class TopicConfiguration {
    @Bean
    fun topicPublisher() = TopicServiceMemory()
}

@Controller
class WebSocketController(val topicManager: ChatTopicService) {

    @MessageMapping("/userdist")
    fun getUserFeed(uid: UUID): Flux<out Message<TopicMessageKey, Any>> = topicManager.receiveSourcedEvents(uid)

}
