package com.demo.chat.controller.webflux

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallResult
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST recall routes. Each route returns one JSON object with the coverage
 * flag. Request bodies use the shared sealed request types.
 *
 * The routes no longer name a media type. A Mono of one value serializes as one
 * JSON object, and NDJSON would frame that object as a stream of one line.
 */
@RestController
@RequestMapping("/message/recall")
@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])
class ChatMessageRecallController<T>(
    private val recallService: MessageRecallService<T>,
) {

    @PostMapping("/topic")
    fun recallInTopic(@RequestBody req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>> =
        recallService.recallInTopic(req)

    @PostMapping("/user")
    fun recallByUser(@RequestBody req: UserRecallRequest<T>): Mono<MessageRecallResult<T>> =
        recallService.recallByUser(req)

    @PostMapping("/global")
    fun recallGlobal(@RequestBody req: GlobalRecallRequest): Mono<MessageRecallResult<T>> =
        recallService.recallGlobal(req)
}
