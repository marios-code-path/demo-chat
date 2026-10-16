package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.SmartLifecycle

/**
 * Holds one lease for each store service that this process creates. See
 * `CHAT-lswjobhz`.
 *
 * It claims after the node id guard and before `RootKeyStartup`, so it holds
 * the leases before any start scan reads the store. It releases in [stop] and,
 * after a failed refresh, in [destroy].
 *
 * [services] is read at start, when every singleton exists. It maps bean
 * names to the store service beans. `ServiceLeaseNames` derives the lease
 * names from them.
 */
class ServiceLeaseGuard(
    private val store: ServiceLeaseStore,
    private val services: () -> Map<String, Any>,
    private val nodeId: NodeId,
    owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
    private val deadlines: ClaimScheduler = scheduler,
    clock: () -> Long = System::nanoTime,
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(ServiceLeaseGuard::class.java)
    private val holder = "node ${nodeId.value} ${owner.value}"
    private val keeper = LeaseKeeper(
        "The store service leases of app.nodeid=${nodeId.value}", props, context, scheduler, deadlines,
        "service-lease-close", clock,
    )

    @Volatile
    private var running = false

    /** The names that this guard claimed, in claim order. */
    @Volatile
    var claimed: List<String> = emptyList()
        private set

    override fun start() {
        val names = ServiceLeaseNames.of(services())
        if (names.isEmpty()) {
            running = true
            return
        }
        val taken = mutableListOf<String>()
        keeper.begin()
        try {
            names.forEach { name ->
                val sentAt = keeper.now()
                when (val result = store.claim(name, holder, props.ttl).timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> {
                        taken.add(name)
                        keeper.add(lease(name), sentAt)
                    }
                    is ClaimResult.Denied -> throw ServiceLeaseException(name, store.scope, result.holder, props.ttl)
                    is ClaimResult.Lost, null -> throw IllegalStateException(
                        "The ${store.backendName} store returned $result from a claim of $name. " +
                            "A claim returns Granted or Denied."
                    )
                }
            }
            keeper.held()
        } catch (failure: Throwable) {
            keeper.releaseAll()
            throw failure
        }
        claimed = taken.toList()
        log.info("Store service leases {} claimed in the {}", taken, store.scope)
        running = true
    }

    override fun stop() {
        keeper.releaseAll()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.SERVICE_GUARD

    override fun destroy() {
        keeper.releaseAll()
        scheduler.shutdownNow()
        if (deadlines !== scheduler) deadlines.shutdownNow()
    }

    private fun lease(name: String) = object : HeldLease {
        override val label = "The store service $name"

        override fun renew() = store.renew(name, holder, props.ttl)

        override fun release() = store.release(name, holder)

        override fun deniedMessage(holder: String): String =
            ServiceLeaseException(name, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "The store service $name is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }
}
