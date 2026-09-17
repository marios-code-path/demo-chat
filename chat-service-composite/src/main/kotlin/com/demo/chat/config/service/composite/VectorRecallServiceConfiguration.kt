package com.demo.chat.config.service.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.impl.ComposedJobRecordWriter
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.MessageReindexServiceImpl
import com.demo.chat.service.composite.impl.VectorCoveragePolicyImpl
import com.demo.chat.service.composite.impl.VectorIndexJobStoreImpl
import com.demo.chat.service.composite.impl.VectorIndexStartupAction
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.service.vector.JobRecordCodec
import com.demo.chat.service.vector.JobRecordWriter
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorWriteMode
import com.demo.chat.service.vector.VectorTrust
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.UUID

/**
 * Recall wiring for the composite services. The class-level gate is the
 * composite selector; each bean needs both recall selectors set. The
 * VectorStore bean comes from the active vector provider module.
 *
 * Interim capability wiring: @ConditionalOnProperty stands in for
 * @ProvidesCapability until the capability mechanism lands.
 *
 * Every store comes from a provider interface. The composite context exposes
 * no MessagePersistence bean by type, so a parameter of that type would not
 * resolve. CompositeServiceBeansConfiguration reads the same way.
 */
@Configuration
@ConditionalOnProperty("app.service.composite")
class VectorRecallServiceConfiguration<T : Any, V, Q>(
    private val typeUtil: TypeUtil<T>,
    private val persistenceBeans: PersistenceServiceBeans<T, V>,
    private val indexBeans: IndexServiceBeans<T, V, Q>,
    private val pubSubBeans: PubSubServiceBeans<T, V>,
    /**
     * The mapper the deployment registers, not a private one.
     *
     * Boot registers every Module bean, and `JacksonModules` declares one bean
     * for each chat module, so this mapper can read a Key. A private mapper
     * would let the stored shape and the deployed shape drift apart, and the
     * backend decode tests bind this same bean.
     */
    private val codecMapper: ObjectMapper,
) {
    /** One value per process start. It separates this run from an earlier one. */
    private val incarnationId: String = UUID.randomUUID().toString()

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun vectorIndexState(): VectorIndexState<T> = InMemoryVectorIndexState()

    /**
     * The durable job store.
     *
     * The worker key comes from the topic key generator, which the store also
     * calls for every job topic.
     *
     * **This makes the context refresh depend on key generation.** The read
     * blocks once, during the refresh, and it runs under every startup value,
     * `report` included. A key generator that cannot answer therefore fails the
     * startup of a deployment that would never have built a job. The wait is
     * bounded at 30 seconds, so the failure is loud rather than a hang.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun vectorIndexJobStore(
        embeddingIdentity: EmbeddingIdentity,
        @Value("\${app.nodeid}") nodeId: Int,
        @Value("\${app.key.type}") keyType: String,
    ): VectorIndexJobStoreImpl<T, V, Q> {
        val workerKey: Key<T> = persistenceBeans.topicPersistence()
            .key()
            .block(Duration.ofSeconds(30))
            ?: throw ChatException("The topic key generator answered with no key for the vector worker.")

        return VectorIndexJobStoreImpl(
            topicPersistence = persistenceBeans.topicPersistence(),
            topicIndex = indexBeans.topicIndex(),
            pubsub = pubSubBeans.pubSubService(),
            keyValueStore = persistenceBeans.keyValuePersistence(),
            codec = IndexJobCodec(codecMapper),
            nodeId = nodeId,
            keyType = keyType,
            embeddingIdentity = embeddingIdentity,
            incarnationId = incarnationId,
            workerKey = workerKey,
        )
    }

    /**
     * Writes one job record through persistence, the message index, and pubsub.
     *
     * Every current deployment binds message data to String, which the design
     * states. So the cast below holds for every composition this repository
     * builds. A deployment with another value type needs its own converter.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    @Suppress("UNCHECKED_CAST")
    fun jobRecordWriter(): JobRecordWriter<T> =
        ComposedJobRecordWriter<T, V, Q>(
            messagePersistence = persistenceBeans.messagePersistence(),
            messageIndex = indexBeans.messageIndex(),
            pubsub = pubSubBeans.pubSubService(),
            codec = JobRecordCodec(codecMapper),
            asValue = { text: String -> text as V },
        )

    /**
     * The coverage decision.
     *
     * The policy also takes the TypeUtil bean. It breaks a tie between two jobs
     * of one instant, and a text compare would put "9" above "10".
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun vectorCoveragePolicy(
        jobStore: VectorIndexJobStore<T>,
        embeddingIdentity: EmbeddingIdentity,
        @Value("\${app.nodeid}") nodeId: Int,
        @Value("\${app.key.type}") keyType: String,
        @Value("\${app.vector.index.trust:none}") trust: String,
    ): VectorCoveragePolicy<T> =
        VectorCoveragePolicyImpl(
            jobStore = jobStore,
            trust = trustOf(trust),
            incarnationId = incarnationId,
            nodeId = nodeId,
            keyType = keyType,
            embeddingIdentity = embeddingIdentity,
            typeUtil = typeUtil,
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun messageVectorIndexer(
        vectorStore: VectorStore,
        @Value("\${app.key.type}") keyType: String,
        // The write mode follows the provider. Only the embedded provider
        // refuses a repeated document id, and a removal on redis would log an
        // error for every new message.
        @Value("\${app.service.core.vector}") vectorSelector: String,
        state: VectorIndexState<T>,
        jobStore: VectorIndexJobStore<T>,
    ): MessageVectorIndexer<T> =
        VectorStoreMessageVectorIndexer(
            vectorStore,
            MessageDocumentMapper(typeUtil, keyType),
            state,
            jobStore,
            VectorWriteMode.forVectorSelector(vectorSelector),
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun messageReindexService(
        indexer: MessageVectorIndexer<T>,
        state: VectorIndexState<T>,
        jobStore: VectorIndexJobStore<T>,
        recordWriter: JobRecordWriter<T>,
    ): MessageReindexService<T> =
        MessageReindexServiceImpl(
            persistence = persistenceBeans.messagePersistence(),
            indexer = indexer,
            state = state,
            jobStore = jobStore,
            recordWriter = recordWriter,
        )

    /**
     * The startup action.
     *
     * `VectorIndexStartupAction` holds the order, and the order is the rule.
     * The sweep runs, then the policy selects, then the state adopts, and only
     * then does a rebuild start.
     *
     * Only `rebuild` starts a job. `report` is the default, because real
     * embedding throughput is still unmeasured. An automatic rebuild could
     * delay readiness or send uncontrolled external requests.
     *
     * The listener binds to ApplicationReadyEvent, not to context refresh. A
     * rebuild reads and writes through the same stores the deployment serves,
     * so it must not run while those beans are still starting.
     *
     * `subscribe()` returns at once. `reindex.start()` already hands the scan
     * to its own scheduler, and the sweep and the coverage read must not hold
     * the event thread.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun vectorIndexStartupAction(
        jobStore: VectorIndexJobStore<T>,
        policy: VectorCoveragePolicy<T>,
        state: VectorIndexState<T>,
        reindex: MessageReindexService<T>,
        @Value("\${app.nodeid}") nodeId: Int,
        @Value("\${app.key.type}") keyType: String,
        @Value("\${app.vector.index.startup:report}") startup: String,
    ): ApplicationListener<ApplicationReadyEvent> {
        val action = VectorIndexStartupAction(
            jobStore = jobStore,
            policy = policy,
            state = state,
            reindex = reindex,
            nodeId = nodeId,
            keyType = keyType,
            incarnationId = incarnationId,
            startRebuild = startupOf(startup) == "rebuild",
        )
        return ApplicationListener { action.run().subscribe() }
    }

    private fun trustOf(value: String): VectorTrust = when (value.lowercase()) {
        "none" -> VectorTrust.NONE
        "stored" -> VectorTrust.STORED
        else -> throw ChatException(
            "app.vector.index.trust takes 'none' or 'stored'. It received '$value'."
        )
    }

    private fun startupOf(value: String): String = when (value.lowercase()) {
        "report", "rebuild" -> value.lowercase()
        else -> throw ChatException(
            "app.vector.index.startup takes 'report' or 'rebuild'. It received '$value'."
        )
    }
}
