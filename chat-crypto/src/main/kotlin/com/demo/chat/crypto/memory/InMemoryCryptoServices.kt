package com.demo.chat.crypto.memory

import com.demo.chat.domain.*
import com.demo.chat.service.core.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory device registry — stores device registrations keyed by deviceId.
 * For production, this would be backed by Cassandra or Postgres.
 */
class InMemoryDeviceService<T> : DeviceService<T> {
    private val devices = ConcurrentHashMap<T, DeviceRegistration<T>>()

    override fun registerDevice(device: DeviceRegistration<T>): Mono<Void> =
        Mono.fromRunnable<Void> {
            devices[device.key.id] = device
        }.then()

    override fun revokeDevice(deviceId: Key<T>): Mono<Void> =
        Mono.fromRunnable<Void> {
            devices.remove(deviceId.id)
        }.then()

    override fun getDevices(userId: Key<T>): Flux<DeviceRegistration<T>> =
        Flux.fromIterable(devices.values.filter { it.userId == userId })

    override fun getDevice(deviceId: Key<T>): Mono<DeviceRegistration<T>> =
        Mono.justOrEmpty(devices[deviceId.id])
}

/**
 * In-memory pre-key pool — stores one-time pre-key bundles per device.
 * consumeBundle removes the bundle (one-time use). When the pool drops below
 * a threshold (5), refillSignal returns true to trigger client refill.
 */
class InMemoryPreKeyService<T> : PreKeyService<T> {
    private val bundles = ConcurrentHashMap<T, MutableList<PreKeyBundle<T>>>()
    private val refillThreshold = 5

    override fun publishBundle(bundle: PreKeyBundle<T>): Mono<Void> =
        Mono.fromRunnable<Void> {
            bundles.computeIfAbsent(bundle.deviceId.id) { mutableListOf() }.add(bundle)
        }.then()

    override fun consumeBundle(deviceId: Key<T>): Mono<PreKeyBundle<T>> =
        Mono.fromCallable {
            val list = bundles[deviceId.id]
            if (list.isNullOrEmpty()) {
                throw IllegalStateException("No pre-key bundles available for device $deviceId")
            }
            list.removeFirst()
        }

    override fun getBundle(deviceId: Key<T>): Mono<PreKeyBundle<T>> =
        Mono.fromCallable {
            bundles[deviceId.id]?.firstOrNull()
        }

    override fun countBundles(deviceId: Key<T>): Mono<Long> =
        Mono.just((bundles[deviceId.id]?.size ?: 0).toLong())

    override fun refillSignal(deviceId: Key<T>): Mono<Boolean> =
        Mono.just((bundles[deviceId.id]?.size ?: 0) < refillThreshold)
}

/**
 * In-memory encrypted message relay — the "blind relay."
 * Stores ciphertext envelopes keyed by (deviceId, conversationId, seq).
 * The server never decrypts — it routes by metadata only.
 */
class InMemoryEncryptedMessageService<T> : EncryptedMessageService<T> {
    private val envelopes = ConcurrentHashMap<T, MutableList<EncryptedEnvelope<T>>>()
    private val frankingService = InMemoryFrankingService<T>()

    override fun send(envelope: EncryptedEnvelope<T>): Mono<FrankingTag<T>> {
        return Mono.fromCallable {
            // Store in recipient's device inbox
            envelopes.computeIfAbsent(envelope.recipientDeviceId.id) { mutableListOf() }
                .add(envelope)
            // Generate franking tag (proof of message existence for abuse reporting)
            frankingService.generateTagSync(
                envelope.conversationId,
                envelope.seq,
                envelope.senderDeviceId,
                envelope.messageKind,
                envelope.ciphertext
            )
        }
    }

    override fun fetchByConversation(conversationId: Key<T>, afterSeq: Long, limit: Int): Flux<EncryptedEnvelope<T>> =
        Flux.fromIterable(
            envelopes.values.flatten()
                .filter { it.conversationId == conversationId && it.seq > afterSeq }
                .sortedBy { it.seq }
                .take(limit)
        )

    override fun fetchByDevice(deviceId: Key<T>, afterSeq: Long, limit: Int): Flux<EncryptedEnvelope<T>> =
        Flux.fromIterable(
            (envelopes[deviceId.id] ?: emptyList())
                .filter { it.seq > afterSeq }
                .sortedBy { it.seq }
                .take(limit)
        )

    override fun ackDelivery(deviceId: Key<T>, conversationId: Key<T>, seq: Long): Mono<Void> =
        Mono.empty()
}

/**
 * In-memory conversation sequence allocator — uses an AtomicLong per conversation
 * for simple monotonic seq allocation. Production would use Postgres row-lock
 * inside the same transaction that writes the message.
 */
class InMemoryConversationSeqService<T> : ConversationSeqService<T> {
    private val cursors = ConcurrentHashMap<T, AtomicLong>()

    override fun nextSeq(conversationId: Key<T>): Mono<Long> =
        Mono.fromCallable {
            cursors.computeIfAbsent(conversationId.id) { AtomicLong(0) }
                .incrementAndGet()
        }

    override fun getCursor(conversationId: Key<T>): Mono<ConversationCursor<T>> =
        Mono.fromCallable {
            ConversationCursor.create(conversationId, cursors[conversationId.id]?.get() ?: 0L)
        }

    override fun initCursor(conversationId: Key<T>): Mono<Void> =
        Mono.fromRunnable<Void> {
            cursors.computeIfAbsent(conversationId.id) { AtomicLong(0) }
        }.then()
}

/**
 * In-memory conversation epoch service — tracks membership boundaries.
 * Each membership change (join/leave) starts a new epoch.
 */
class InMemoryConversationEpochService<T> : ConversationEpochService<T> {
    private val epochs = ConcurrentHashMap<T, MutableList<ConversationEpoch<T>>>()

    override fun startEpoch(conversationId: Key<T>): Mono<ConversationEpoch<T>> =
        Mono.fromCallable {
            @Suppress("UNCHECKED_CAST")
            val epochKey = Key.funKey(java.util.UUID.randomUUID().toString() as T)
            val epoch = ConversationEpoch.create(
                epochKey,
                conversationId,
                (epochs[conversationId.id]?.size ?: 0) + 1
            )
            epochs.computeIfAbsent(conversationId.id) { mutableListOf() }.add(epoch)
            epoch
        }

    override fun endEpoch(epochId: Key<T>): Mono<Void> =
        Mono.empty() // In-memory, epochs are implicit

    override fun getCurrentEpoch(conversationId: Key<T>): Mono<ConversationEpoch<T>> =
        Mono.fromCallable {
            epochs[conversationId.id]?.last()
        }

    override fun getEpochs(conversationId: Key<T>): Flux<ConversationEpoch<T>> =
        Flux.fromIterable(epochs[conversationId.id] ?: emptyList())
}

/**
 * In-memory franking service — generates HMAC-style tags for abuse reporting.
 * Production would use a rotated server-side key.
 */
class InMemoryFrankingService<T> : FrankingService<T> {
    private val currentKeyId = java.util.concurrent.atomic.AtomicInteger(1)
    private val key = "franking-secret-key-v1".toByteArray()

    @Suppress("UNCHECKED_CAST")
    fun generateTagSync(
        conversationId: Key<T>,
        seq: Long,
        senderDeviceId: Key<T>,
        messageKind: MessageKind,
        ciphertext: ByteArray
    ): FrankingTag<T> {
        val tagInput = "${conversationId.id}:$seq:${senderDeviceId.id}:${messageKind}".toByteArray()
        val combined = tagInput + ciphertext
        val tag = java.security.MessageDigest.getInstance("SHA-256").digest(combined + key)
        val tagId: T = java.util.UUID.randomUUID().toString() as T
        return FrankingTag.create(
            Key.funKey(tagId),
            conversationId,
            seq,
            senderDeviceId,
            messageKind,
            tag,
            currentKeyId.get()
        )
    }

    override fun generateTag(
        conversationId: Key<T>,
        seq: Long,
        senderDeviceId: Key<T>,
        messageKind: MessageKind,
        ciphertextHash: ByteArray
    ): Mono<FrankingTag<T>> =
        Mono.fromCallable { generateTagSync(conversationId, seq, senderDeviceId, messageKind, ciphertextHash) }

    override fun verifyTag(tag: FrankingTag<T>, ciphertextHash: ByteArray): Mono<Boolean> =
        Mono.fromCallable {
            val tagInput = "${tag.conversationId.id}:${tag.seq}:${tag.senderDeviceId.id}:${tag.messageKind}".toByteArray()
            val combined = tagInput + ciphertextHash
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(combined + key)
            expected.contentEquals(tag.tag)
        }

    override fun rotateKey(): Mono<Int> =
        Mono.fromCallable {
            currentKeyId.incrementAndGet()
        }
}
