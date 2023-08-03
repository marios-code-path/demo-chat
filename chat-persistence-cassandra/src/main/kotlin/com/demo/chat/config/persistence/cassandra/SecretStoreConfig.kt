package com.demo.chat.config.persistence.cassandra

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.persistence.cassandra.impl.CredentialSecretsStoreCassandra
import com.demo.chat.persistence.cassandra.repository.KeyCredentialRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["secrets"])
class SecretStoreConfig<T>(
    val keyService: IKeyService<T>,
    val repo: KeyCredentialRepository<T>
) : SecretsStoreBeans<T> {

    override fun secretsStore(): SecretsStore<T> =
        CredentialSecretsStoreCassandra(keyService, repo)
}