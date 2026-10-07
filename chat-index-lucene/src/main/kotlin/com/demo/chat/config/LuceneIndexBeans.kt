package com.demo.chat.config

import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.StartupIndexLoad

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.LuceneIndexLoad
import com.demo.chat.index.lucene.LuceneIndexNames
import com.demo.chat.index.lucene.LuceneIndexRegistry
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.*
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.index.lucene.storage.LuceneStorages
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
/**
 * The constructor carries @Autowired. The default values make Kotlin emit a
 * second, synthetic constructor, and Spring then finds no single constructor
 * to use. The defaults keep a direct construction in a test short.
 */
open class LuceneIndexBeans<T> @Autowired constructor(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>,
    private val keyValueFieldEntries: ObjectProvider<KeyValueIndexFieldsEntry>,
    @Value("\${app.index.lucene.root:#{null}}") private val root: String? = null,
    @Value("\${app.key.type:#{null}}") private val keyType: String? = null,
    @Value("\${app.nodeid:#{null}}") private val nodeId: Int? = null,
) : IndexServiceBeans<T, String, IndexSearchRequest> {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The index stores the id text. A found key carries the root of the index
     * domain, read when the query runs. See `CHAT-avduuqwp`.
     */
    private fun keysIn(domain: ChatDomain): (String) -> Key<T> =
        { str -> Key.of(typeUtil.fromString(str), rootKeys.of(domain).id) }

    /**
     * Where the six indexes live. An absent root keeps them in memory. A blank
     * root fails the start. See CHAT-ybtirmgj.
     */
    @Bean
    open fun luceneStorage(): LuceneStorage =
        LuceneStorages.of(root, keyType, nodeId).also { logger.info("lucene index storage: ${it.summary}") }

    @Bean(destroyMethod = "close")
    override fun userIndex(): UserIndexService<T, IndexSearchRequest> =
        UserLuceneIndex(IndexEntryEncoder.ofUser(), keysIn(ChatDomain.USER)) { t -> t.key }

    @Bean(destroyMethod = "close")
    override fun messageIndex(): MessageIndexService<T, String, IndexSearchRequest> =
        MessageLuceneIndex(IndexEntryEncoder.ofMessage(), keysIn(ChatDomain.MESSAGE)) { t -> t.key }

    @Bean(destroyMethod = "close")
    override fun topicIndex(): TopicIndexService<T, IndexSearchRequest> =
        TopicLuceneIndex(IndexEntryEncoder.ofTopic(), keysIn(ChatDomain.MESSAGE_TOPIC)) { t -> t.key }

    @Bean(destroyMethod = "close")
    override fun membershipIndex(): MembershipIndexService<T, IndexSearchRequest> =
        MembershipLuceneIndex(IndexEntryEncoder.ofTopicMembership(), keysIn(ChatDomain.TOPIC_MEMBERSHIP)) { t ->
            Key.of(t.key, rootKeys.of(ChatDomain.TOPIC_MEMBERSHIP).id)
        }

    @Bean(destroyMethod = "close")
    override fun authMetadataIndex(): AuthMetaIndex<T, IndexSearchRequest> =
        AuthMetaIndexLucene(typeUtil, rootKeys)

    @Bean(destroyMethod = "close")
    override fun KVPairIndex(): KeyValueIndexService<T, IndexSearchRequest> =
        KeyValueLuceneIndex(
            typeUtil,
            rootKeys,
            IndexEntryEncoder.ofKeyValueFields(
                TypedKeyValueIndexFields(keyValueFieldEntries.orderedStream().toList())
            )
        )

    /** The operator view of the six indexes. The actuator endpoint reads it. */
    @Bean
    open fun luceneIndexRegistry(): LuceneIndexRegistry = LuceneIndexRegistry(
        luceneStorage(),
        linkedMapOf(
            LuceneIndexNames.USER to userIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.MESSAGE to messageIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.TOPIC to topicIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.MEMBERSHIP to membershipIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.AUTH to authMetadataIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.KEY_VALUE to KVPairIndex() as LuceneIndex<*, *>,
        ),
    )

    /**
     * The load opens the Lucene auth index against the auth store. A grant read
     * queries it first. The start sequence runs this load after the roots load
     * and before readiness, so a stored grant is found after a restart. See
     * `CHAT-bafkgkko` and `CHAT-ybtirmgj`.
     */
    @Bean
    open fun luceneAuthIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.AUTH, authMetadataIndex(), persistence) { it.authMetaPersistence() }

    /**
     * **The other Lucene indexes open against their stores at start too.**
     * Without a root each one lives in process memory and builds at each start.
     * With a root each one reuses its files when they match the store.
     *
     * The user index matters most. `InitialUsersService` finds an identity
     * user by its handle. With an empty user index it found none, so each
     * restart on a persistent store created a new `Admin`, with a new key and
     * a new set of `Admin` wildcard rows. Measured on 2026-10-04. See
     * `CHAT-uxgdzpag`.
     *
     * One bean per index. Each one opens its index against its store.
     */
    @Bean
    open fun luceneUserIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.USER, userIndex(), persistence) { it.userPersistence() }

    @Bean
    open fun luceneTopicIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.TOPIC, topicIndex(), persistence) { it.topicPersistence() }

    @Bean
    open fun luceneMessageIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.MESSAGE, messageIndex(), persistence) { it.messagePersistence() }

    @Bean
    open fun luceneMembershipIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.MEMBERSHIP, membershipIndex(), persistence) { it.membershipPersistence() }

    /**
     * A stored pair of a type with no registered fields fails this load, and so
     * the start. That is the rule of the key-value index: an unregistered type
     * throws rather than indexing nothing. See `CHAT-sgdtqhof`.
     */
    @Bean
    open fun luceneKeyValueIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.KEY_VALUE, KVPairIndex(), persistence) { it.keyValuePersistence() }

    /** No store gives a null store. LuceneIndexLoad decides by the storage mode. */
    @Suppress("UNCHECKED_CAST")
    private fun load(
        name: String,
        index: Any,
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        pick: (PersistenceServiceBeans<*, *>) -> PersistenceStore<*, *>,
    ): StartupIndexLoad = LuceneIndexLoad(
        index as LuceneIndex<T, Any>,
        name,
        luceneStorage(),
        persistence.ifAvailable?.let(pick) as PersistenceStore<T, Any>?,
    )
}
