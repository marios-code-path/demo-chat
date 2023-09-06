package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono



interface IKeyRestMapping<T> : IKeyService<T> {

    @PostMapping("/new",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restKey(@RequestBody req: KindRequest): Mono<out Key<T>> = key(Class.forName(req.kind))

    @DeleteMapping("/rem/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@PathVariable id: T): Mono<Void> = rem(Key.funKey(id))

    @GetMapping("/exists/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restExists(@PathVariable id: T): Mono<Boolean> = exists(Key.funKey(id))

}

data class KindRequest(val kind: String)