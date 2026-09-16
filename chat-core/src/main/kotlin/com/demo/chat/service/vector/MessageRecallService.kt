package com.demo.chat.service.vector

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import reactor.core.publisher.Mono

interface MessageRecallService<T> {
    fun recallInTopic(req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>>
    fun recallByUser(req: UserRecallRequest<T>): Mono<MessageRecallResult<T>>
    fun recallGlobal(req: GlobalRecallRequest): Mono<MessageRecallResult<T>>
}
