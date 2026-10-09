package com.demo.chat.test.deploy.cassandra

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext

/** The `I` safe-repeat contract over index `cassandra`. Node id 44. */
@Tag("integration")
class CassandraIndexSafeRepeatTests : CompositionSafeRepeatBase() {
    private lateinit var running: ConfigurableApplicationContext

    override fun context(): ApplicationContext = running

    override val roomName = "saferepeatcassandraindex"

    @BeforeAll
    fun startComposition() {
        running = CassandraSafeRepeatLaunch.start(44)
    }

    @AfterAll
    fun stopComposition() {
        if (!::running.isInitialized) return
        running.close()
        check(!running.isActive) { "The safe-repeat context did not close." }
    }

    override fun handler(): DomainCommandHandler<Long, String> = MessageIndexHandler(indexBeans.messageIndex())

    override fun results(command: AcceptedCommand<Long, String>): Int =
        indexBeans.messageIndex().findBy(converters.topicIdToQuery(ByIdRequest(room)))
            .filter { it.id == command.message.key.id }
            .count().block(wait)!!.toInt()
}
