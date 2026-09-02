package com.demo.chat.service.vector

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import reactor.core.publisher.Flux

interface MessageRecallService<T> {
    fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>>
}