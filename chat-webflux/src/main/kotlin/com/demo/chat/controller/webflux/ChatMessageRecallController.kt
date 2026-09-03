package com.demo.chat.controller.webflux

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/**
 * REST recall routes. Each route returns NDJSON hits, like the topic listen
 * route. Request bodies use the shared sealed request types.
 */
@RestController
@RequestMapping("/message/recall")
@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])
class ChatMessageRecallController<T>(
    private val recallService: MessageRecallService<T>,
) {

    @PostMapping("/topic", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallInTopic(@RequestBody req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>> =
        recallService.recallInTopic(req)

    @PostMapping("/user", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallByUser(@RequestBody req: UserRecallRequest<T>): Flux<MessageRecallHit<T>> =
        recallService.recallByUser(req)

    @PostMapping("/global", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallGlobal(@RequestBody req: GlobalRecallRequest): Flux<MessageRecallHit<T>> =
        recallService.recallGlobal(req)
}
