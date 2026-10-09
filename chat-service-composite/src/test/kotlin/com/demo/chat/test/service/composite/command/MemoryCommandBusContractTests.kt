package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.test.command.CommandBusContractTests
import java.time.Duration

class MemoryCommandBusContractTests : CommandBusContractTests() {
    override fun succeedingHandlers(): List<DomainCommandHandler<Long, String>> =
        CommandFixtures.PIU.map { ScriptedHandler(it) }

    override fun start(handlers: List<DomainCommandHandler<Long, String>>): BusUnderTest {
        val runtime = MemoryCommandRuntime(
            KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil, handlers,
            CommandBusSettings(CompletionRequirement(setOf(BackendId.PERSISTENCE)), Duration.ofSeconds(5), Duration.ofSeconds(30)),
        )
        return BusUnderTest(runtime.bus, runtime.completions) { runtime.close() }
    }
}
