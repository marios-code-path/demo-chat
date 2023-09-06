package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

data class KR (val id: Long)

interface IKeyRestMapping<T> : IKeyService<T> {
    @PostMapping("/new",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restKey(@RequestBody req: KindRequest): Mono<out Key<T>> = key(Class.forName(req.kind))
    @DeleteMapping("/rem")
    override fun rem(key: Key<T>): Mono<Void>
    @GetMapping("/exists",
        consumes = [MediaType.TEXT_PLAIN_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    override fun exists(key: Key<T>): Mono<Boolean>
}

data class KindRequest(val kind: String)