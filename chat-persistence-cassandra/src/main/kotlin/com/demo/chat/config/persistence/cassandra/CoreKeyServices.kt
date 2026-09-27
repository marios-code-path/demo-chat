package com.demo.chat.config.persistence.cassandra

import com.demo.chat.persistence.cassandra.impl.CassandraStoreShapeCheck

import com.demo.chat.service.core.StoreShapeCheck


import com.datastax.oss.driver.api.core.CqlSession

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.persistence.cassandra.impl.RootKeyStoreCassandra
import com.demo.chat.service.core.RootKeyStore
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate


@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "cassandra")
class CoreKeyServices<T : Any>(
    val reactiveTemplate: ReactiveCassandraTemplate,
    val keyGenerator: IKeyGenerator<T>,
    val rootKeys: RootKeys<T>,
) : KeyServiceBeans<T> {
    @Bean
    override fun keyService(): IKeyService<T> = KeyServiceCassandra(reactiveTemplate, keyGenerator, rootKeys)

    /** The shape of the key registry tables. It runs before the root keys load. See `CHAT-avduuqwp`, T7. */
    @Bean
    fun cassandraKeyShapeCheck(session: CqlSession): StoreShapeCheck =
        CassandraStoreShapeCheck.lazy(session, CassandraStoreShapeCheck.KEY_TABLES)

    @Bean
    fun rootKeyStore(): RootKeyStore<T> = RootKeyStoreCassandra(reactiveTemplate)
}