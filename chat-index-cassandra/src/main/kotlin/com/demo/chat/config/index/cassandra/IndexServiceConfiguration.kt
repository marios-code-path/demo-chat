package com.demo.chat.config.index.cassandra

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.TypedKeyValueIndexFields
import org.springframework.beans.factory.ObjectProvider
import com.demo.chat.index.cassandra.repository.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
class IndexServiceConfiguration {
    @Bean
    fun <T> indexServiceBeans(
        cassandra: ReactiveCassandraTemplate,
        userHandleRepo: ChatUserHandleRepository<T>,
        nameRepo: TopicByNameRepository<T>,
        byMemberRepo: TopicMembershipByMemberRepository<T>,
        byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
        byUserRepo: ChatMessageByUserRepository<T>,
        byTopicRepo: ChatMessageByTopicRepository<T>,
        principalRepo: AuthMetadataByPrincipalRepository<T>,
        targetRepo: AuthMetadataByTargetRepository<T>,
        kvIndexRepo: KeyValueIndexRepository<T>,
        kvIndexByIdRepo: KeyValueIndexByIdRepository<T>,
        keyValueFieldEntries: ObjectProvider<KeyValueIndexFieldsEntry>,
        typeUtil: TypeUtil<T>
    ): IndexServiceBeans<T, String, Map<String, String>> = CassandraIndexServices(
        cassandra,
        userHandleRepo,
        nameRepo,
        byMemberRepo,
        byMemberOfRepo,
        byUserRepo,
        byTopicRepo,
        principalRepo,
        targetRepo,
        kvIndexRepo,
        kvIndexByIdRepo,
        TypedKeyValueIndexFields(keyValueFieldEntries.orderedStream().toList()),
        typeUtil
    )
}
