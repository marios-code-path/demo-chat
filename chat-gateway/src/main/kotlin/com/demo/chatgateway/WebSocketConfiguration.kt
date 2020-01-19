package com.demo.chatgateway

import com.demo.chatevents.service.TopicMessagingServiceMemory
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

//@ConfigurationProperties("ws-gateway-config")
class WebSocketConfigurationProperties(var port: Int) {
    constructor() : this(0) {}
}

@Configuration
class WebSocketConfiguration() {

    @Bean
    fun reactiveServerFactory(): ReactiveWebServerFactory = NettyReactiveWebServerFactory(websocketConfigurationProperties().port)

    @Bean
    fun websocketConfigurationProperties() = WebSocketConfigurationProperties(9090)

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }
}

@Configuration
class TopicConfiguration {
    @Bean
    fun topicPublisher() = TopicMessagingServiceMemory()
}