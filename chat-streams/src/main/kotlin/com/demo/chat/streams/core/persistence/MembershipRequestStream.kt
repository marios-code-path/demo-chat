package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.PersistenceStore
import com.demo.chat.streams.core.TopicMembershipRequest
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import java.util.function.Function

class MembershipRequestStream<T, Q>(
    private val membPersist: PersistenceStore<T, TopicMembership<T>>,
    private val membIndex: MembershipIndexService<T, Q>
) {
    @Bean
    fun receiveMembershipRequest() = Function<Flux<TopicMembershipRequest<T>>, Flux<TopicMembership<T>>> { memReq ->
        memReq
            .flatMap { req ->
                membPersist
                    .key()
                    .map { key -> TopicMembership.create(key.id, req.uid, req.roomId) }
            }
            .flatMap { membership ->
                membIndex
                    .add(membership)
                    .thenReturn(membership)
            }

    }
}