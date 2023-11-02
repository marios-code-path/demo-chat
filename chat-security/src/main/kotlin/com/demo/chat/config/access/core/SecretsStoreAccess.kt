package com.demo.chat.config.access.core

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Mono

interface SecretsStoreAccess<T> : SecretsStore<T> {

    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'READ')")
    override fun getStoredCredentials(key: Key<T>): Mono<String>

    @PreAuthorize("@chatAccess.hasAccessTo(#keyCredential.component1(), 'WRITE')")
    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void>
}