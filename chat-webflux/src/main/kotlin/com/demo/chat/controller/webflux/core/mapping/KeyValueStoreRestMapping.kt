package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.KeyValueStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface KeyValueStoreRestMapping<T> :
    PersistenceRestMapping<T, KeyValuePair<T, Any>>,
    KeyValueStore<T, Any> {

    @GetMapping("/byIds")
    fun restByIds(ids: List<Key<T>>): Flux<KeyValuePair<T, Any>> = typedByIds(ids, Any::class.java)

    // The controller is generic in T, so Jackson never sees T when it binds the
    // request body: it infers the key type from the JSON alone. The key arrives as
    // Any and typeUtil() converts it to the key type this store actually uses.
    fun typeUtil(): TypeUtil<T>

    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun addKv(@RequestBody req: KVRequest) = key()
        .flatMap { key ->
            add(KeyValuePair.create(Key.funKey(typeUtil().assignFrom(req.key)), req.data))
                .thenReturn(key)
        }
}

data class KVRequest(val key: Any, val data: Any)