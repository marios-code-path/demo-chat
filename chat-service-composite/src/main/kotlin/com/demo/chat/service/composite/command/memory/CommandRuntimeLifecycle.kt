package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.LeasePhases
import org.springframework.context.SmartLifecycle

/**
 * Stops the command runtime before the lease guards release. Its dispatcher
 * writes the store on its own schedule, and bean destruction runs after the
 * release. See `CHAT-lswjobhz`.
 */
class CommandRuntimeLifecycle(private val runtime: MemoryCommandRuntime<*, *>) : SmartLifecycle {
    @Volatile
    private var running = false

    override fun start() {
        running = true
    }

    override fun stop() {
        runtime.close()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.WRITERS
}
