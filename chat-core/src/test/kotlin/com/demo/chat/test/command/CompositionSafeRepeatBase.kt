package com.demo.chat.test.command

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyAllocator
import org.junit.jupiter.api.TestInstance
import org.springframework.context.ApplicationContext
import java.time.Instant
import java.util.UUID

/**
 * Builds commands in one room of a running composition. Each subclass names
 * one provider, and it supplies the running context through [context].
 *
 * The base reads every bean from that context. It uses no field injection,
 * because two of the source fixtures build their context with
 * `SpringApplicationBuilder`, where no injection runs. Each lookup names a
 * holder type with one bean. `IKeyService` itself is ambiguous, because
 * `KeyServiceController` implements it too.
 *
 * One test instance serves the whole class, so the class uses one room and
 * a builder subclass starts one context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CompositionSafeRepeatBase : SafeRepeatContractTests() {
    /** The running composition. A subclass injects it or builds it. */
    abstract fun context(): ApplicationContext

    /** Letters only. The Lucene name index splits a name on a hyphen. See `CHAT-hajmhslp`. */
    abstract val roomName: String

    @Suppress("UNCHECKED_CAST")
    protected val keyService: IKeyService<Long>
        get() = (context().getBean(KeyServiceBeans::class.java) as KeyServiceBeans<Long>).keyService()

    @Suppress("UNCHECKED_CAST")
    protected val persistenceBeans: PersistenceServiceBeans<Long, String>
        get() = context().getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>

    @Suppress("UNCHECKED_CAST")
    protected val indexBeans: IndexServiceBeans<Long, String, Any>
        get() = context().getBean(IndexServiceBeans::class.java) as IndexServiceBeans<Long, String, Any>

    @Suppress("UNCHECKED_CAST")
    protected val converters: RequestToQueryConverters<Any>
        get() = context().getBean(RequestToQueryConverters::class.java) as RequestToQueryConverters<Any>

    @Suppress("UNCHECKED_CAST")
    protected val pubsubBeans: PubSubServiceBeans<Long, String>
        get() = context().getBean(PubSubServiceBeans::class.java) as PubSubServiceBeans<Long, String>

    @Suppress("UNCHECKED_CAST")
    private val keyGenerator: IKeyGenerator<Long>
        get() = context().getBean(IKeyGenerator::class.java) as IKeyGenerator<Long>

    @Suppress("UNCHECKED_CAST")
    private val rootKeys: RootKeys<Long>
        get() = context().getBean(RootKeys::class.java) as RootKeys<Long>

    @Suppress("UNCHECKED_CAST")
    private val composite: CompositeServiceBeans<Long, String>
        get() = context().getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    protected val room: Long by lazy { composite.topicService().addRoom(ByStringRequest(roomName)).block(wait)!!.id }

    override fun newCommand(text: String): AcceptedCommand<Long, String> {
        val key = KeyAllocator(keyGenerator, rootKeys).allocate(ChatDomain.MESSAGE)
        val admin = rootKeys.admin().id
        val messageKey = SimpleMessageKey(key.id, key.root, admin, room, Instant.now())
        return AcceptedCommand(
            UUID.randomUUID().toString(), admin, "sr-${UUID.randomUUID()}", key.root,
            CommandOperation.RECORD_MESSAGE, room, 1, Message.create(messageKey, "$text ${key.id}", true),
            BackendId.entries.toSet(), 1,
        )
    }
}
