package com.demo.chat.controller.webflux

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.core.mapping.TopicPubSubRestMapping
import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono


@RestController
@RequestMapping("/pubsub")
@ConditionalOnProperty(prefix = "app.controller", name = ["pubsub"])
class PubSubRestController<T, V>(private val that: PubSubServiceBeans<T, V>) : TopicPubSubRestMapping<T, V>,
    TopicPubSubService<T, V> by that.pubSubService()
