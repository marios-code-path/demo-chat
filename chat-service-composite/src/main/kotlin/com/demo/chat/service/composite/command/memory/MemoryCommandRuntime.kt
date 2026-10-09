package com.demo.chat.service.composite.command.memory

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.core.KeyAllocator
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration

/**
 * The Stage 1 `memory` command bus in one process. Construction is the
 * startup check of each active handler: a missing or unsupported safe-repeat
 * contract fails it before admission accepts any command. Case 27.
 */
class MemoryCommandRuntime<T : Any, V>(
    allocator: KeyAllocator<T>,
    typeUtil: TypeUtil<T>,
    handlers: List<DomainCommandHandler<T, V>>,
    settings: CommandBusSettings,
    policy: BackendExecutionPolicy = BackendExecutionPolicy(recoveryInterval = settings.recoveryInterval),
    faults: AdmissionFaults = AdmissionFaults.NONE,
    rescanInterval: Duration = Duration.ofSeconds(1),
    dispatchFaults: DispatchFaults = DispatchFaults.NONE,
    clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private val scheduler = Schedulers.newBoundedElastic(16, Int.MAX_VALUE, "command-runtime")
    private val log = DispatchLog<T, V>()
    val completions = MemoryCommandStatusStore<T>()
    private val dispatcher: CommandDispatcher<T, V>
    val bus: MemoryDomainCommandBus<T, V>

    init {
        handlers.forEach { SafeRepeatContracts.requireSupported(it.descriptor) }
        val duplicated = handlers.groupBy { it.descriptor.backend }.filterValues { it.size > 1 }.keys
        check(duplicated.isEmpty()) { "More than one handler is active for $duplicated." }
        val active = handlers.map { it.descriptor.backend }.toSet()
        val missing = settings.requirement.backends - active
        check(missing.isEmpty()) { "The completion requirement names $missing, and no handler is active for it." }

        val runtimes = handlers.associate { it.descriptor.backend to BackendRuntime(it, policy, completions, scheduler, clock) }
        dispatcher = CommandDispatcher(log, runtimes, scheduler, rescanInterval, dispatchFaults)
        bus = MemoryDomainCommandBus(allocator, typeUtil, completions, log, active, policy, dispatcher::signal, faults, clock = clock)
    }

    override fun close() {
        dispatcher.close()
        scheduler.dispose()
    }
}
