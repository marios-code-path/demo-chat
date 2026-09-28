package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.deploy.event.RootKeyUpdatedEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.StartupIndexLoad
import com.demo.chat.service.core.StoreShapeCheck
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.SmartLifecycle

/**
 * One root key source step. It loads the roots of this process into
 * [RootKeys]. It returns false when the process loads no roots, which is the
 * [RootKeySource.NONE] role. It publishes no event.
 */
fun interface RootKeyLoadStep {
    fun load(): Boolean
}

/**
 * The start sequence of the root keys. See `CHAT-avduuqwp` and `CHAT-bafkgkko`.
 *
 * It runs these steps in order:
 *
 * 1. Every store shape check.
 * 2. The root key source step.
 * 3. Every index load.
 * 4. The readiness events.
 *
 * It is a [SmartLifecycle] with a phase below both servers. The reactive web
 * server starts at phase `Integer.MAX_VALUE - 2048`, and the RSocket server
 * at the default phase `Integer.MAX_VALUE`. So no server accepts a request
 * before this sequence completes. A failed step throws from [start]. The
 * context refresh then fails, and the process does not start.
 *
 * A [RootKeySource.NONE] process runs the shape checks. It loads no roots,
 * runs no index load, and publishes no readiness event, because an index
 * entry reads a root when a query runs.
 */
class RootKeyStartup<T>(
    private val source: RootKeySource,
    private val shapeChecks: ObjectProvider<StoreShapeCheck>,
    private val step: ObjectProvider<RootKeyLoadStep>,
    private val indexLoads: ObjectProvider<StartupIndexLoad>,
    private val rootKeys: RootKeys<T>,
    private val publisher: DeploymentEventPublisher,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false

    override fun start() {
        shapeChecks.orderedStream().forEach { it.check() }
        val loaded = (step.ifUnique ?: throw ChatException(
            "The root key source is $source, and no single step loads it. Check ${RootKeySource.SCHEME}."
        )).load()
        if (loaded) {
            indexLoads.orderedStream().forEach { load ->
                try {
                    load.load().block()
                } catch (e: RuntimeException) {
                    throw ChatException(
                        "An index load failed at start. The process does not start with a partial index. Cause: ${e.message}"
                    ).apply { initCause(e) }
                }
            }
            when (source) {
                RootKeySource.STORE -> publisher.publishEvent(StartupAnnouncementEvent("Root Keys Loaded"))
                RootKeySource.KV -> publisher.publishEvent(RootKeyUpdatedEvent(rootKeys))
                else -> Unit
            }
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        } else {
            logger.info("${RootKeySource.REQUIRED}=false. This process loads no root keys.")
        }
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = PHASE

    companion object {
        /** Below the reactive web server at `Integer.MAX_VALUE - 2048`, and below the RSocket server. */
        const val PHASE = Int.MAX_VALUE - 4096
    }
}
