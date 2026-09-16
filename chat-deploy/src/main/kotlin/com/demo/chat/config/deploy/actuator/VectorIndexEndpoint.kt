package com.demo.chat.config.deploy.actuator

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

/** One actuator answer. The status is in process, and the jobs are durable. */
data class VectorIndexReport<T>(
    val status: VectorIndexStatus<T>,
    val jobs: List<IndexJob<T>>,
)

/**
 * The operator view of the vector index, and the one manual rebuild trigger.
 *
 * `ActuatorWebSecurityConfiguration` protects every actuator path with the
 * ACTUATOR role, so this class adds no security code.
 *
 * **An operator must set two properties.** The deployments load
 * `management-defaults.yml`, which sets
 * `management.endpoints.enabled-by-default` to false. So `enableByDefault` on
 * the annotation is not enough on its own, and an id that is only exposed still
 * answers 404.
 *
 * ```
 * management.endpoint.vectorindex.enabled=true
 * management.endpoints.web.exposure.include=vectorindex
 * ```
 *
 * No deployment in this repository sets either value.
 *
 * Both gates are needed. `VectorRecallServiceConfiguration` carries the
 * composite gate at class level and the selectors at bean level, so a
 * selector-only gate would expose this endpoint where no composite service
 * exists.
 *
 * One annotation names all three properties, because a class takes one
 * `@ConditionalOnProperty` and the annotation is not repeatable. Every named
 * property must be present, which is the same rule as the two split gates.
 */
@Component
@Endpoint(id = "vectorindex", enableByDefault = true)
@ConditionalOnProperty(
    name = [
        "app.service.composite",
        "app.service.core.vector",
        "app.service.core.embedding",
    ]
)
class VectorIndexEndpoint<T>(
    private val reindex: MessageReindexService<T>,
    private val jobStore: VectorIndexJobStore<T>,
    private val typeUtil: TypeUtil<T>,
    @Value("\${app.nodeid}") private val nodeId: Int,
    @Value("\${app.key.type}") private val keyType: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The status and the recent jobs, newest first.
     *
     * The operation answers with a publisher. `ReactiveWebOperationAdapter`
     * treats an operation result as a `Publisher`, and it unwraps a `Mono`
     * before it builds the response. So no thread blocks here.
     *
     * The bound sits inside the chain. A store that hangs then returns the
     * status with an empty job list, exactly as a store error does. The status
     * lives in process and is always available, so one durable failure must not
     * take the whole view away from an operator.
     */
    @ReadOperation
    fun readVectorIndex(): Mono<VectorIndexReport<T>> =
        recentJobs()
            .timeout(READ_TIMEOUT)
            .onErrorResume { error ->
                logger.error("The vector index endpoint could not read its jobs", error)
                Mono.just(emptyList())
            }
            .map { jobs ->
                VectorIndexReport(
                    status = reindex.status(),
                    jobs = jobs,
                )
            }

    /**
     * Starts one rebuild and returns at once.
     *
     * The answer carries `accepted` beside the claim snapshot. A rejected
     * trigger means another run holds the claim, and it creates no job. An
     * accepted first trigger reports `running=true` and `activeJob=null`, and
     * **it never promises the job key**. The run creates its job after the
     * claim, and on another scheduler.
     *
     * A client polls the read operation until the active job is not null, or
     * until running is false. An immediate second read does not close that
     * race, so this method performs none.
     */
    @WriteOperation
    fun startVectorIndexRebuild(): Mono<VectorIndexTriggerResult<T>> = reindex.start()

    // The listing defers. A store that reads at assembly time would run before
    // a subscriber arrives, and an actuator result is assembled early.
    private fun recentJobs(): Mono<List<IndexJob<T>>> = Mono.defer {
        jobStore.listJobTopics()
            .filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }
            .flatMap { topic -> jobStore.readJob(topic.key) }
            // The name and the record are two stored things. A local name over
            // a foreign record must not reach an operator as this deployment's
            // job. The coverage policy and the release sweep check the same
            // pair.
            .filter { job -> ownedByThisDeployment(job) }
            .sort(
                compareByDescending<IndexJob<T>> { job -> job.startedAt }
                    .thenByDescending(Comparator<T> { a, b -> typeUtil.compare(a, b) }) { job -> job.key.id }
            )
            .take(MAX_JOBS)
            .collectList()
    }

    private fun ownedByThisDeployment(job: IndexJob<T>): Boolean =
        if (job.nodeId == nodeId && job.keyType == keyType) {
            true
        } else {
            logger.error(
                "A job topic of node {} and key type '{}' holds a record of node {} and key type '{}'. " +
                    "The endpoint skips that job.",
                nodeId,
                keyType,
                job.nodeId,
                job.keyType,
            )
            false
        }

    companion object {
        /** The recall limit cap, at RequestResponse.kt. One bound for both reads. */
        const val MAX_JOBS = 50L

        /** The bound on one durable read. It runs inside the chain. */
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
