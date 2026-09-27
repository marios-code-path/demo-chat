package com.demo.chat.config.index.cassandra

import com.demo.chat.service.core.StoreShapeCheck

import com.demo.chat.service.core.ColumnShapeCheck

import com.demo.chat.domain.ChatException

import com.datastax.oss.driver.api.core.CqlSession

import com.demo.chat.domain.knownkey.RootKeys

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
    /**
     * The shape of the index tables that carry grant roots. It runs before the
     * root keys load, also when another backend provides the keys. See
     * `CHAT-avduuqwp`, T7.
     */
    @Bean
    fun cassandraIndexShapeCheck(session: CqlSession): StoreShapeCheck = StoreShapeCheck {
        val keyspace = session.keyspace.map { it.asInternal() }.orElseThrow {
            ChatException("The Cassandra session names no keyspace, so the store shape cannot be checked.")
        }
        ColumnShapeCheck("Keyspace $keyspace", INDEX_TABLES) {
            session.execute("SELECT table_name, column_name FROM system_schema.columns WHERE keyspace_name = ?", keyspace)
                .map { row -> row.getString("table_name")!! to row.getString("column_name")!! }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
        }.check()
    }

    companion object {
        val INDEX_TABLES: Map<String, Set<String>> = mapOf(
            "auth_metadata_principal" to setOf("principal_root", "target_root"),
            "auth_metadata_target" to setOf("principal_root", "target_root"),
        )
    }

    @Bean
    fun <T : Any> indexServiceBeans(
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
        typeUtil: TypeUtil<T>,
        rootKeys: RootKeys<T>,
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
        typeUtil,
        rootKeys
    )
}
