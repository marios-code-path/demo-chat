package com.demo.chat.controller.composite.mapping

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallResult
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

/**
 * RSocket routes for message recall. The routes are flat, so the controller
 * must not add a class-level route prefix.
 */
interface MessageRecallControllerMapping<T> : MessageRecallService<T> {

    @MessageMapping("message-recall-topic")
    override fun recallInTopic(req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>>

    @MessageMapping("message-recall-user")
    override fun recallByUser(req: UserRecallRequest<T>): Mono<MessageRecallResult<T>>

    @MessageMapping("message-recall-global")
    override fun recallGlobal(req: GlobalRecallRequest): Mono<MessageRecallResult<T>>
}
