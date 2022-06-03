package com.demo.chat.deploy.app.memory

import com.demo.chat.config.index.memory.LuceneIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.service.index.AuthMetadataIndex
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.messaging.MemoryTopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class MemoryResourceConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    fun memoryPubSub(): TopicPubSubService<UUID, String> = MemoryTopicPubSubService()

    @Configuration
    class PersistenceBeans(keyFactory: KeyServiceBeans<UUID>)
        : InMemoryPersistenceBeans<UUID, String>(keyFactory.keyService())

    @Configuration
    class IndexBeans : LuceneIndexBeans<UUID, String, String>(
            StringToKeyEncoder { i -> Key.funKey(UUID.fromString(i)) },
            IndexEntryEncoder { t ->
                listOf(
                        Pair("key", t.key.id.toString()),
                        Pair("handle", t.handle),
                        Pair("name", t.name)
                )
            },
            IndexEntryEncoder { t ->
                listOf(
                        Pair("key", t.key.id.toString()),
                        Pair("text", t.data)
                )
            },
            IndexEntryEncoder { t ->
                listOf(
                        Pair("key", t.key.id.toString()),
                        Pair("name", t.data)
                )
            },
            IndexEntryEncoder { t ->
                listOf(
                        Pair("key", Key.funKey(t.key).toString()),
                        Pair(MembershipIndexService.MEMBER, t.member.toString()),
                        Pair(MembershipIndexService.MEMBEROF, t.memberOf.toString())
                )
            },
            IndexEntryEncoder {  t ->
                listOf(
                    Pair(AuthMetadataIndex.PRINCIPAL, t.principal.id.toString()),
                    Pair(AuthMetadataIndex.TARGET, t.target.id.toString()),
                    Pair(AuthMetadataIndex.ID, t.key.id.toString())
                )
            }
        )
}