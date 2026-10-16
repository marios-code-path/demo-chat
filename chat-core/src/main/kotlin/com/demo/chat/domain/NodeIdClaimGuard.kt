package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.SmartLifecycle

/**
 * Holds the store-side lease on `app.nodeid` for the life of the process.
 *
 * The guard claims when its lifecycle phase starts, after the connection
 * factories and before `RootKeyStartup`. A duplicate therefore fails startup
 * before the application is ready and before the process serves normal
 * traffic.
 *
 * It releases in [stop], while the connection factories still run. It also
 * releases in [destroy], because a failed refresh may not run [stop]. See
 * `CHAT-ocpojbyy` and `CHAT-lswjobhz`.
 *
 * [scheduler] runs the renewals. [deadlines] runs the close deadline. A test
 * can pass one manual scheduler for both.
 */
class NodeIdClaimGuard(
    private val stores: List<NodeIdClaimStore>,
    private val nodeId: NodeId,
    private val owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
    private val deadlines: ClaimScheduler = scheduler,
    clock: () -> Long = System::nanoTime,
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(NodeIdClaimGuard::class.java)
    private val keeper = LeaseKeeper(
        "app.nodeid=${nodeId.value}", props, context, scheduler, deadlines, "nodeid-claim-close", clock
    )

    @Volatile
    private var running = false

    override fun start() {
        if (stores.isEmpty()) {
            running = true
            return
        }

        val taken = mutableListOf<NodeIdClaimStore>()
        keeper.begin()
        try {
            stores.sortedBy { it.backendName }.forEach { store ->
                val sentAt = keeper.now()
                when (val result = store.claim(nodeId, owner.value, props.ttl)
                    .timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> {
                        taken.add(store)
                        keeper.add(lease(store), sentAt)
                    }
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
            keeper.held()
        } catch (failure: Throwable) {
            keeper.releaseAll()
            throw failure
        }

        log.info("app.nodeid={} claimed in {}", nodeId.value, taken.joinToString { it.scope })
        running = true
    }

    override fun stop() {
        keeper.releaseAll()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.NODE_ID_GUARD

    override fun destroy() {
        keeper.releaseAll()
        scheduler.shutdownNow()
        if (deadlines !== scheduler) deadlines.shutdownNow()
    }

    private fun lease(store: NodeIdClaimStore) = object : HeldLease {
        override val label = "app.nodeid=${nodeId.value} on ${store.backendName}"

        override fun renew() = store.renew(nodeId, owner.value, props.ttl)

        override fun release() = store.release(nodeId, owner.value)

        override fun deniedMessage(holder: String): String =
            NodeIdClaimException(nodeId, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "app.nodeid=${nodeId.value} is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }
}
