package com.demo.deploy.config

import com.demo.chat.codec.Codec
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

open class CassandraKeyServiceFactory<T>(
        private val template: ReactiveCassandraTemplate,
        private val keyGenerator: Codec<Unit, T>) {
    open fun keyService(): IKeyService<T> =
            KeyServiceCassandra(template, keyGenerator)
}