package com.demo.chat.service.composite.command.handler

import com.demo.chat.domain.Message
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageVectorIndexer
import reactor.core.publisher.Mono

private fun descriptorOf(backend: BackendId) = HandlerDescriptor(
    backend, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE, CommandOperation.IMPORT_MESSAGE),
    SafeRepeatContracts.SUPPORTED.getValue(backend),
)

/** `P`: register the assigned key, then store the message. `P` succeeds only after both. Decision 2. */
class MessagePersistenceHandler<T, V>(
    private val keys: IKeyService<T>,
    private val persistence: MessagePersistence<T, V>,
) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.PERSISTENCE)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> =
        keys.register(command.message.key).then(Mono.defer { persistence.add(command.message) })
}

/** `I`: Lucene replaces by the exact key. Cassandra writes by full primary key. */
class MessageIndexHandler<T, V, Q>(private val index: MessageIndexService<T, V, Q>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.INDEX)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = index.add(command.message)
}

/** `V`: the indexer takes text. Every composition binds the message value to `String`. */
class MessageVectorHandler<T, V>(private val indexer: MessageVectorIndexer<T>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.VECTOR)

    @Suppress("UNCHECKED_CAST")
    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = indexer.add(command.message as Message<T, String>)
}

/** `U`: one publication task per command. Decision 13. */
class MessagePubSubHandler<T : Any, V>(private val publications: RoomPublications<T, V>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.PUBSUB)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> =
        publications.publish(command.commandId, command.message)
}
