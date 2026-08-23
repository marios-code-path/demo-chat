package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.IKeyService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono


interface IKeyRestMapping<T> : IKeyService<T> {

    // T is erased where Spring resolves the path segment, so the segment arrives
    // as the String it is on the wire and typeUtil() converts it to the key type.
    fun typeUtil(): TypeUtil<T>

    @PostMapping("/new",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restKey(@RequestBody req: KindRequest): Mono<out Key<T>> = key(Class.forName(req.kind))

    @DeleteMapping("/rem/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@PathVariable id: String): Mono<Void> = rem(Key.funKey(typeUtil().fromString(id)))

    @GetMapping("/exists/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restExists(@PathVariable id: String): Mono<Boolean> = exists(Key.funKey(typeUtil().fromString(id)))

}

data class KindRequest(val kind: String)