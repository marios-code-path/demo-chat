package com.demo.chat.config.index.cassandra

import com.demo.chat.service.core.StoreShapeCheck

import com.demo.chat.service.core.ColumnShapeCheck

import com.demo.chat.domain.ChatException

import com.datastax.oss.driver.api.core.CqlSession

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.TypeUtil
import java.util.UUID
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
     * The shape of the index tables that carry grant roots and messages. It
     * runs before the root keys load, also when another backend provides the
     * keys. See `CHAT-avduuqwp`, T7. The message id columns must also have the
     * type of the key. See `CHAT-xcmpudyb`.
     */
    @Bean
    fun cassandraIndexShapeCheck(session: CqlSession, typeUtil: TypeUtil<*>): StoreShapeCheck = StoreShapeCheck {
        val keyspace = session.keyspace.map { it.asInternal() }.orElseThrow {
            ChatException("The Cassandra session names no keyspace, so the store shape cannot be checked.")
        }
        ColumnShapeCheck("Keyspace $keyspace", INDEX_TABLES, indexTypes(typeUtil)) {
            session.execute("SELECT table_name, column_name, type FROM system_schema.columns WHERE keyspace_name = ?", keyspace)
                .groupBy({ it.getString("table_name")!! }, { it.getString("column_name")!! to it.getString("type")!! })
                .mapValues { it.value.toMap() }
        }.check()
    }

    companion object {
        val INDEX_TABLES: Map<String, Set<String>> = mapOf(
            "auth_metadata_principal" to setOf("id", "principal_root", "target_root"),
            "auth_metadata_target" to setOf("id", "principal_root", "target_root"),
            "auth_metadata_by_id" to setOf("target", "principal", "target_root", "principal_root"),
            "chat_message_index_by_id" to setOf("user_id", "topic_id", "msg_time"),
        )

        /**
         * The CQL type of a message id for [typeUtil]. A `Long` id is `bigint`.
         * A `UUID` id is `timeuuid`. `CassandraStoreShapeCheck.idType` in
         * `chat-persistence-cassandra` holds the same rule.
         */
        fun idType(typeUtil: TypeUtil<*>): String = when (typeUtil.empty()) {
            is Long -> "bigint"
            is UUID -> "timeuuid"
            else -> throw ChatException("No Cassandra message id type for the key type of ${typeUtil::class.java.name}.")
        }

        /** The message id type of each message index table, for [typeUtil]. */
        fun indexTypes(typeUtil: TypeUtil<*>): Map<String, Map<String, String>> = idType(typeUtil).let { id ->
            listOf("chat_message_user", "chat_message_topic", "chat_message_index_by_id").associateWith { mapOf("msg_id" to id) }
        }
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
        messageByIdRepo: ChatMessageIndexByIdRepository<T>,
        principalRepo: AuthMetadataByPrincipalRepository<T>,
        targetRepo: AuthMetadataByTargetRepository<T>,
        authByIdRepo: AuthMetadataByIdRepository<T>,
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
        messageByIdRepo,
        principalRepo,
        targetRepo,
        authByIdRepo,
        kvIndexRepo,
        kvIndexByIdRepo,
        TypedKeyValueIndexFields(keyValueFieldEntries.orderedStream().toList()),
        typeUtil,
        rootKeys
    )
}
