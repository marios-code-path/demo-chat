package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import reactor.core.publisher.Mono

interface SecretsRestMapping<T> : SecretsStore<T> {
    @GetMapping("/get")
    override fun getStoredCredentials(key: Key<T>): Mono<String>
    @PutMapping("/add")
    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void>
    @PostMapping("/compare")
    override fun compareSecret(keyCredential: KeyCredential<T>): Mono<Boolean>
}