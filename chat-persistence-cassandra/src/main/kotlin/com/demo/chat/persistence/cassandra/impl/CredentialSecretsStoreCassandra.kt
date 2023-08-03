package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.CredKey
import com.demo.chat.persistence.cassandra.domain.KeyCredentialById
import com.demo.chat.persistence.cassandra.repository.KeyCredentialRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.UserCredentialSecretsStore
import reactor.core.publisher.Mono

open class CredentialSecretsStoreCassandra<T>(val keyService: IKeyService<T>,
                                              private val credRepo: KeyCredentialRepository<T>
) : UserCredentialSecretsStore<T> {
        override fun getStoredCredentials(key: Key<T>): Mono<String> = credRepo.findByKeyId(key.id)
            .map { it.data }

    override fun addCredential(kc: KeyCredential<T>): Mono<Void> {
        return credRepo.save(KeyCredentialById(CredKey(kc.key.id, "CRED"), kc.data))
            .then()
    }
}