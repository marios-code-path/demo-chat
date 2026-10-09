package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext

/** The `P` safe-repeat contract over key `redis` and persistence `redis`. Node id 40. */
@Tag("integration")
class RedisKeyAndPersistenceSafeRepeatTests : CompositionSafeRepeatBase() {
    private lateinit var running: ConfigurableApplicationContext

    override fun context(): ApplicationContext = running

    override val roomName = "saferepeatredispersistence"

    @BeforeAll
    fun startComposition() {
        running = RedisSafeRepeatLaunch.start("redis-safe-repeat-persistence", 40)
    }

    @AfterAll
    fun stopComposition() {
        if (!::running.isInitialized) return
        running.close()
        check(!running.isActive) { "The safe-repeat context did not close." }
    }

    override fun handler(): DomainCommandHandler<Long, String> =
        MessagePersistenceHandler(keyService, persistenceBeans.messagePersistence())

    override fun results(command: AcceptedCommand<Long, String>): Int {
        val key = command.message.key
        if (keyService.rootOf(key.id).block(wait) != key.root) return 0
        return persistenceBeans.messagePersistence().byIds(listOf(key)).count().block(wait)!!.toInt()
    }
}
