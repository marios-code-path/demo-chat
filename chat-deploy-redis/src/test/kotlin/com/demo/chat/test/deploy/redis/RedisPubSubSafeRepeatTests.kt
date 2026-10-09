package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import reactor.core.Disposable
import java.util.concurrent.CopyOnWriteArrayList

/** The `U` safe-repeat contract over pubsub `redis-pubsub`. Node id 41. */
@Tag("integration")
class RedisPubSubSafeRepeatTests : CompositionSafeRepeatBase() {
    private lateinit var running: ConfigurableApplicationContext

    override fun context(): ApplicationContext = running

    override val roomName = "saferepeatredispubsub"

    private val heard = CopyOnWriteArrayList<Long>()
    private var listener: Disposable? = null

    @Suppress("UNCHECKED_CAST")
    private val publications: RoomPublications<Long, String>
        get() = context().getBean(RoomPublications::class.java) as RoomPublications<Long, String>

    @BeforeAll
    fun startComposition() {
        running = RedisSafeRepeatLaunch.start("redis-safe-repeat-pubsub", 41)
    }

    @BeforeEach
    fun listenOnceAndClear() {
        if (listener == null) {
            pubsubBeans.pubSubService().open(room).block(wait)
            listener = pubsubBeans.pubSubService().listenTo(room).subscribe { heard.add(it.key.id) }
            Thread.sleep(settle.toMillis())
        }
        heard.clear()
    }

    @AfterAll
    fun stopComposition() {
        listener?.dispose()
        if (!::running.isInitialized) return
        running.close()
        check(!running.isActive) { "The safe-repeat context did not close." }
    }

    override fun handler(): DomainCommandHandler<Long, String> = MessagePubSubHandler(publications)

    override fun results(command: AcceptedCommand<Long, String>): Int = heard.count { it == command.message.key.id }
}
