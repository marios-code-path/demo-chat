package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.command.RequestIdentity
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.CommandFingerprint
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.RequestIds
import com.demo.chat.service.core.KeyAllocator
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Test seams for decision 7. Each runs after one staging write. Production uses [NONE]. */
interface AdmissionFaults {
    fun afterMapping() {}
    fun afterDispatchEntry() {}
    fun afterStaging() {}
    fun beforeNotify() {}

    companion object {
        val NONE = object : AdmissionFaults {}
    }
}

/**
 * Admission of decision 7. A per-identity lock is the critical section.
 * Admission stages the mapping, the dispatch entry, and the status record
 * behind one [CommitMarker], then exposes all three with one commit write.
 * A failure before the commit removes all three. A notification failure after
 * the commit does not reject the admission. The dispatcher rescans the log.
 */
class MemoryDomainCommandBus<T : Any, V>(
    private val allocator: KeyAllocator<T>,
    private val typeUtil: TypeUtil<T>,
    private val status: MemoryCommandStatusStore<T>,
    private val log: DispatchLog<T, V>,
    private val obligations: Set<BackendId>,
    private val policy: BackendExecutionPolicy,
    private val notifier: () -> Unit,
    private val faults: AdmissionFaults = AdmissionFaults.NONE,
    private val commandIds: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
) : DomainCommandBus<T, V> {

    private class Mapping<T>(val fingerprint: String, val receipt: Receipt<T>, val marker: CommitMarker)

    private val logger = LoggerFactory.getLogger(MemoryDomainCommandBus::class.java)
    private val mappings = ConcurrentHashMap<RequestIdentity<T>, Mapping<T>>()
    private val locks = ConcurrentHashMap<RequestIdentity<T>, Any>()

    override fun submit(submission: CommandSubmission<T, V>): Mono<Receipt<T>> =
        Mono.fromCallable { admit(submission) }

    fun committedMappings(): Int = mappings.values.count { it.marker.committed }

    fun stagedMappings(): Int = mappings.values.count { !it.marker.committed }

    private fun admit(submission: CommandSubmission<T, V>): Receipt<T> {
        RequestIds.requireValid(submission.requestId)
        val identity = RequestIdentity(submission.owner, submission.requestId)
        val fingerprint = CommandFingerprint.of(
            CommandOperation.RECORD_MESSAGE,
            typeUtil.toString(submission.sender),
            typeUtil.toString(submission.dest),
            submission.content,
        )
        val lock = locks.computeIfAbsent(identity) { Any() }
        val receipt = synchronized(lock) {
            val existing = mappings[identity]
            if (existing != null) {
                if (existing.fingerprint != fingerprint) throw RequestConflictException(submission.requestId)
                return existing.receipt
            }
            stageAndCommit(identity, submission, fingerprint)
        }
        try {
            faults.beforeNotify()
            notifier()
        } catch (e: Exception) {
            logger.warn("The dispatcher notification failed after the commit of ${receipt.commandId}. The rescan dispatches it.", e)
        }
        return receipt
    }

    private fun stageAndCommit(
        identity: RequestIdentity<T>,
        submission: CommandSubmission<T, V>,
        fingerprint: String,
    ): Receipt<T> {
        val key = allocator.allocate(ChatDomain.MESSAGE)
        val messageKey: MessageKey<T> = SimpleMessageKey(key.id, key.root, submission.sender, submission.dest, clock.instant())
        val command = AcceptedCommand(
            commandId = commandIds(),
            owner = submission.owner,
            requestId = submission.requestId,
            rootId = key.root,
            operation = CommandOperation.RECORD_MESSAGE,
            orderingKey = submission.dest,
            schemaVersion = SCHEMA_VERSION,
            message = Message.create(messageKey, submission.content, true),
            obligations = obligations,
            executionPolicyVersion = policy.version,
        )
        val receipt = Receipt(command.commandId, messageKey)
        val marker = CommitMarker()
        val mapping = Mapping(fingerprint, receipt, marker)
        val entry = DispatchEntry(command, marker)

        // Every staging write sits inside the guard, so a failure between two
        // writes removes the writes before it. Each removal is safe to repeat.
        try {
            mappings[identity] = mapping
            faults.afterMapping()
            log.stage(entry)
            faults.afterDispatchEntry()
            status.stage(command, receipt, marker)
            faults.afterStaging()
            marker.commit()
        } catch (e: Throwable) {
            marker.rollBack()
            mappings.remove(identity, mapping)
            log.remove(entry)
            status.remove(command.commandId)
            throw e
        }
        return receipt
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
