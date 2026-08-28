package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ConfigurableApplicationContext

/**
 * Holds the store-side lease on `app.nodeid` for the life of the process.
 *
 * The guard claims during context refresh. A duplicate therefore fails
 * startup before the application is ready and before the process serves
 * normal traffic.
 *
 * The guard is the only place that blocks. The stores stay reactive.
 */
class NodeIdClaimGuard(
    private val stores: List<NodeIdClaimStore>,
    private val nodeId: NodeId,
    private val owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    private val context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler
) : InitializingBean, DisposableBean {

    private val log = LoggerFactory.getLogger(NodeIdClaimGuard::class.java)

    private var granted: List<NodeIdClaimStore> = emptyList()
    private var renewHandle: AutoCloseable? = null
    private var deadlineHandle: AutoCloseable? = null

    override fun afterPropertiesSet() {
        if (stores.isEmpty()) {
            return
        }

        val ordered = stores.sortedBy { it.backendName }
        val taken = mutableListOf<NodeIdClaimStore>()

        try {
            ordered.forEach { store ->
                when (val result = store.claim(nodeId, owner.value, props.ttl)
                    .timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> taken.add(store)
                    is ClaimResult.Denied ->
                        throw NodeIdClaimException(nodeId, store.scope, result.holder, props.ttl)
                    is ClaimResult.Lost ->
                        throw IllegalStateException(
                            "The ${store.backendName} store returned Lost from a claim. " +
                                "A claim returns Granted or Denied."
                        )
                    null ->
                        throw IllegalStateException(
                            "The ${store.backendName} store returned no result from a claim."
                        )
                }
            }
        } catch (failure: Throwable) {
            releaseAll(taken.reversed())
            throw failure
        }

        granted = taken
        log.info("app.nodeid={} claimed in {}", nodeId.value, taken.joinToString { it.scope })
        armDeadline()
        renewHandle = scheduler.schedulePeriodic(props.renewInterval) { renewOnce() }
    }

    override fun destroy() {
        renewHandle?.close()
        deadlineHandle?.close()
        scheduler.shutdownNow()
        releaseAll(granted.reversed())
        granted = emptyList()
    }

    private fun armDeadline() {
        deadlineHandle?.close()
        deadlineHandle = scheduler.scheduleOnce(props.closeDeadline) {
            closeContext(
                "app.nodeid=${nodeId.value} was not renewed within ${props.closeDeadline.seconds}s. " +
                    "The lease may have expired. Closing the application context."
            )
        }
    }

    private fun renewOnce() {
        granted.forEach { store ->
            val result = try {
                store.renew(nodeId, owner.value, props.ttl).timeout(props.operationTimeout).block()
            } catch (error: Throwable) {
                log.warn(
                    "app.nodeid={} renew failed on {}. Retrying at the next interval. Cause: {}",
                    nodeId.value, store.backendName, error.message
                )
                return
            }

            when (result) {
                is ClaimResult.Granted -> Unit
                is ClaimResult.Denied -> {
                    closeContext(
                        NodeIdClaimException(nodeId, store.scope, result.holder, props.ttl).message!!
                    )
                    return
                }
                is ClaimResult.Lost -> {
                    closeContext(
                        "app.nodeid=${nodeId.value} is no longer held in the ${store.scope}. " +
                            "This process lost its lease. Closing the application context."
                    )
                    return
                }
                null -> {
                    log.warn(
                        "app.nodeid={} renew returned no result from {}. Retrying at the next interval.",
                        nodeId.value, store.backendName
                    )
                    return
                }
            }
        }
        armDeadline()
    }

    // The close runs off the scheduler. A close started on a scheduler thread
    // would run destroy on that same thread, and destroy would wait for it.
    private fun closeContext(reason: String) {
        log.error(reason)
        scheduler.runDetached("nodeid-claim-close") { context.close() }
    }

    private fun releaseAll(ordered: List<NodeIdClaimStore>) {
        ordered.forEach { store ->
            try {
                store.release(nodeId, owner.value).timeout(props.operationTimeout).block()
            } catch (error: Throwable) {
                log.debug(
                    "app.nodeid={} release failed on {}. The lease expires on its own. Cause: {}",
                    nodeId.value, store.backendName, error.message
                )
            }
        }
    }
}
