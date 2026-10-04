package com.demo.chat.config

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.core.PersistedIndexLoad
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.StartupIndexLoad
import reactor.core.publisher.Mono

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["index"],
    havingValue = "lucene",
    matchIfMissing = true
)
open class LuceneIndexBeans<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>,
    private val keyValueFieldEntries: ObjectProvider<KeyValueIndexFieldsEntry>
) : IndexServiceBeans<T, String, IndexSearchRequest> {

    /**
     * The index stores the id text. A found key carries the root of the index
     * domain, read when the query runs. See `CHAT-avduuqwp`.
     */
    private fun keysIn(domain: ChatDomain): (String) -> Key<T> =
        { str -> Key.of(typeUtil.fromString(str), rootKeys.of(domain).id) }

    @Bean
    override fun userIndex(): UserIndexService<T, IndexSearchRequest> =
        UserLuceneIndex(IndexEntryEncoder.ofUser(), keysIn(ChatDomain.USER)) { t -> t.key }

    @Bean
    override fun messageIndex(): MessageIndexService<T, String, IndexSearchRequest> =
        MessageLuceneIndex(IndexEntryEncoder.ofMessage(), keysIn(ChatDomain.MESSAGE)) { t -> t.key }

    @Bean
    override fun topicIndex(): TopicIndexService<T, IndexSearchRequest> =
        TopicLuceneIndex(IndexEntryEncoder.ofTopic(), keysIn(ChatDomain.MESSAGE_TOPIC)) { t -> t.key }

    @Bean
    override fun membershipIndex(): MembershipIndexService<T, IndexSearchRequest> =
        MembershipLuceneIndex(IndexEntryEncoder.ofTopicMembership(), keysIn(ChatDomain.TOPIC_MEMBERSHIP)) { t ->
            Key.of(t.key, rootKeys.of(ChatDomain.TOPIC_MEMBERSHIP).id)
        }

    @Bean
    override fun authMetadataIndex(): AuthMetaIndex<T, IndexSearchRequest> =
        AuthMetaIndexLucene(typeUtil, rootKeys)

    /**
     * The Lucene auth index lives in process memory, so it is empty after a
     * restart. A grant read queries it first. The start sequence runs this
     * load after the roots load and before readiness, so a stored grant is
     * found after a restart. The bean exists only where this in-process index
     * exists. A composition with no local auth store has nothing to load. See
     * `CHAT-bafkgkko`.
     */
    @Bean
    open fun luceneAuthIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.authMetaPersistence() to authMetadataIndex() }

    /**
     * **The other Lucene indexes load at start too.** Each one lives in process
     * memory, so each was empty after a restart. A stored user, room, message,
     * membership or key-value pair was then not found through its index.
     *
     * The user index matters most. `InitialUsersService` finds an identity
     * user by its handle. With an empty user index it found none, so each
     * restart on a persistent store created a new `Admin`, with a new key and
     * a new set of `Admin` wildcard rows. Measured on 2026-10-04. See
     * `CHAT-uxgdzpag`.
     *
     * One bean per index. Each one loads its index from its store.
     */
    @Bean
    open fun luceneUserIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.userPersistence() to userIndex() }

    @Bean
    open fun luceneTopicIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.topicPersistence() to topicIndex() }

    @Bean
    open fun luceneMessageIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.messagePersistence() to messageIndex() }

    @Bean
    open fun luceneMembershipIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.membershipPersistence() to membershipIndex() }

    /**
     * A stored pair of a type with no registered fields fails this load, and so
     * the start. That is the rule of the key-value index: an unregistered type
     * throws rather than indexing nothing. See `CHAT-sgdtqhof`.
     */
    @Bean
    open fun luceneKeyValueIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(persistence) { it.keyValuePersistence() to KVPairIndex() }

    /** A composition with no local store has nothing to load. */
    @Suppress("UNCHECKED_CAST")
    private fun load(
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        pick: (PersistenceServiceBeans<*, *>) -> Pair<PersistenceStore<*, *>, IndexService<T, *, *>>,
    ): StartupIndexLoad {
        val stores = persistence.ifAvailable ?: return StartupIndexLoad { Mono.empty() }
        val (store, index) = pick(stores)
        return PersistedIndexLoad(store as PersistenceStore<T, Any>, index as IndexService<T, Any, *>)
    }

    @Bean
    override fun KVPairIndex(): KeyValueIndexService<T, IndexSearchRequest> =
        KeyValueLuceneIndex(
            typeUtil,
            rootKeys,
            IndexEntryEncoder.ofKeyValueFields(
                TypedKeyValueIndexFields(keyValueFieldEntries.orderedStream().toList())
            )
        )
}