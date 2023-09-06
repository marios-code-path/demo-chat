package com.demo.chat.controller.webflux

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.core.mapping.TopicPubSubRestMapping
import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono


@RestController
@RequestMapping("/pubsub")
class PubSubRestController<T, V>(private val that: PubSubServiceBeans<T, V>) : TopicPubSubRestMapping<T, V>,
    TopicPubSubService<T, V> by that.pubSubService() {
    @PostMapping("/subscribe")
    override fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> = subscribe(req.member, req.topic)

    @PostMapping("/unsubscribe")
    override fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> = unSubscribe(req.member, req.topic)
}
