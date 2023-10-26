package com.demo.chat.controller.core.access

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Mono

interface SecretsStoreAccess<T> : SecretsStore<T> {

    @PreAuthorize("@chatAccess.hasAccessFor(#key, 'READ')")
    override fun getStoredCredentials(key: Key<T>): Mono<String>

    @PreAuthorize("@chatAccess.hasAccessFor(#keyCredential.component1(), 'WRITE')")
    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void>
}