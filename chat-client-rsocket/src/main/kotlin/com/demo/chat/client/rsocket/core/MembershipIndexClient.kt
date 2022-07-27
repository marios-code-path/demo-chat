package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.IndexService
import com.demo.chat.service.MembershipIndexService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class MembershipIndexClient<T, Q>(
    val t: IndexService<T, TopicMembership<T>, Q>,
    val prefix: String,
    val requester: RSocketRequester
) : MembershipIndexService<T, Q>,
    IndexService<T, TopicMembership<T>, Q> by t {
    override fun size(query: Q): Mono<Long> = requester
        .route("${prefix}size")
        .data(query as Any)     //TODO Type fix
        .retrieveMono()
}