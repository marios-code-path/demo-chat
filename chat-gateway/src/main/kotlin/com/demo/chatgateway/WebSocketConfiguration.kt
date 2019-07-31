package com.demo.chatgateway

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import java.time.Duration

@ConfigurationProperties("ws-gateway-config")
class WebSocketConfigurationProperties(val port: Int)


@EnableWebFlux
@Configuration
class WebSocketConfiguration(val config: WebSocketConfigurationProperties) {

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }

    @Bean
    fun urlMapping(): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.urlMap = mapOf(Pair("/dist", webSocketHandler()))

        return simpleMapping
    }

    @Bean
    fun webSocketHandler(): WebSocketHandler = WebSocketHandler {
         session ->
            session.send(
                    Flux.interval(Duration.ofSeconds(1))
                            .map { n -> n!!.toString() }
                            .map<WebSocketMessage> { session.textMessage(it) })
            .and(session.receive()
                    .map {it.payloadAsText }

            )
    }

}