package com.demo.chat.config.persistence.cassandra

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate


@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
class CoreKeyServices<T>(
    val reactiveTemplate: ReactiveCassandraTemplate,
    val keyGenerator: IKeyGenerator<T>
) : KeyServiceBeans<T> {
    @Bean
    override fun keyService(): IKeyService<T> = KeyServiceCassandra(reactiveTemplate, keyGenerator)
}