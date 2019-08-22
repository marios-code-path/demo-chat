package com.demo.chatgateway

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.WebFluxConfigurationSupport
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import reactor.core.publisher.ReplayProcessor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap



@Configuration
class TestWSConfiguration {
    @Configuration
    class MyHttpHandlerAutoConfiguration : HttpHandlerAutoConfiguration()

    @Configuration
    class MyWebFluxConfigurationSupport : WebFluxConfigurationSupport()

    @Bean
    fun serverFactory(): ReactiveWebServerFactory = NettyReactiveWebServerFactory(websocketConfiguration().port)

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }

    @Bean
    fun urlMapping(): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.urlMap = mapOf(Pair("/dist", webSocketHandler()))
        simpleMapping.setCorsConfigurations(mapOf(Pair("*", CorsConfiguration().applyPermitDefaultValues())))
        simpleMapping.order = 10
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

    @Bean
    fun websocketConfiguration() = WebSocketConfigurationProperties(9090)
}

@Controller
class TestMappers {

    val names = ConcurrentHashMap<String, String>()

    @MessageMapping("/userdist")
    fun handle(): Flux<String> = Flux.just("foo", "bar")

    @MessageMapping("/names/{id}")
    fun handleInbox(@DestinationVariable id: String, name: String) {
        names.set(id, name)
    }
}

@Component
class TestClientHandlers {

    val clientProcessor: FluxProcessor<Message<TopicMessageKey, Any>, Message<TopicMessageKey, Any>> = ReplayProcessor.create<Message<TopicMessageKey, Any>>()

    @MessageMapping("/userdist")
    fun getUserFeed(stream: Flux<out Message<TopicMessageKey, Any>>): Disposable =
            clientProcessor
                    .subscribe {
                        clientProcessor.onNext(it)
                    }

    fun receiveEvents(): Flux<Message<TopicMessageKey, Any>> = clientProcessor
            .onBackpressureBuffer()
            .publish()
            .autoConnect()
}