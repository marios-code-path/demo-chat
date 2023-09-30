package com.demo.chat.controller.webflux

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.composite.mapping.ChatMessageServiceRestMapping
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI

@RestController
@RequestMapping("/message")
@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
class ChatMessageServiceController<T>(val beans: CompositeServiceBeans<T, String>,
    val discovery: ClientDiscovery) : ChatMessageServiceRestMapping<T>,
    ChatMessageService<T, String> by beans.messageService() {

    override fun listen(req: ByIdRequest<T>): Mono<String> =
        discovery.getServiceInstance("app.rsocket.topic")
            .map { instance ->
                URI.create("${instance.scheme}://${instance.host}:${instance.port}/").toASCIIString()
            }
}