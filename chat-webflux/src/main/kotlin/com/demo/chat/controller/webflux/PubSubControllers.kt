package com.demo.chat.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.core.mapping.TopicPubSubRestMapping
import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.IKeyService
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
class PubSubRestController<T>(
    private val keyBeans: KeyServiceBeans<T>,
    private val that: PubSubServiceBeans<T, String>
) : TopicPubSubRestMapping<T>,
    TopicPubSubService<T, String> by that.pubSubService() {
    override fun keyService(): IKeyService<T> = keyBeans.keyService()
}
