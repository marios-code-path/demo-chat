package com.demo.chat.controller.webflux

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallResult
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST recall routes. Each route returns one JSON object with the coverage
 * flag. Request bodies use the shared sealed request types.
 *
 * Each route names JSON, and it names only JSON. An absent media type does not
 * stop NDJSON. Content negotiation would still answer an
 * `Accept: application/x-ndjson` request with NDJSON, and the specification
 * says these routes no longer produce it. A request for NDJSON now gets 406.
 */
@RestController
@RequestMapping("/message/recall")
@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])
class ChatMessageRecallController<T>(
    private val recallService: MessageRecallService<T>,
) {

    @PostMapping("/topic", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recallInTopic(@RequestBody req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>> =
        recallService.recallInTopic(req)

    @PostMapping("/user", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recallByUser(@RequestBody req: UserRecallRequest<T>): Mono<MessageRecallResult<T>> =
        recallService.recallByUser(req)

    @PostMapping("/global", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recallGlobal(@RequestBody req: GlobalRecallRequest): Mono<MessageRecallResult<T>> =
        recallService.recallGlobal(req)
}
