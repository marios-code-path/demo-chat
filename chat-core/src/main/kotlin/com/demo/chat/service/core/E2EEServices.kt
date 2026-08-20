package com.demo.chat.service.core

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Device registry service — manages per-device registration and pre-key bundles
 * for end-to-end encryption. The server stores only public material; private keys
 * never leave the device.
 */
interface DeviceService<T> {
    fun registerDevice(device: DeviceRegistration<T>): Mono<Void>
    fun revokeDevice(deviceId: Key<T>): Mono<Void>
    fun getDevices(userId: Key<T>): Flux<DeviceRegistration<T>>
    fun getDevice(deviceId: Key<T>): Mono<DeviceRegistration<T>>
}

/**
 * Pre-key distribution service — manages the one-time pre-key pool per device.
 * When a new session is established, a pre-key is consumed (deleted after retrieval).
 * The server signals the device to refill when the pool runs low.
 */
interface PreKeyService<T> {
    fun publishBundle(bundle: PreKeyBundle<T>): Mono<Void>
    fun consumeBundle(deviceId: Key<T>): Mono<PreKeyBundle<T>>
    fun getBundle(deviceId: Key<T>): Mono<PreKeyBundle<T>>
    fun countBundles(deviceId: Key<T>): Mono<Long>
    fun refillSignal(deviceId: Key<T>): Mono<Boolean>
}

/**
 * Encrypted message relay — the "blind relay" from the plan.
 * Stores ciphertext and routing metadata only. Never sees plaintext.
 * Assigns per-conversation sequence numbers atomically via ConversationCursor.
 */
interface EncryptedMessageService<T> {
    fun send(envelope: EncryptedEnvelope<T>): Mono<FrankingTag<T>>
    fun fetchByConversation(conversationId: Key<T>, afterSeq: Long, limit: Int): Flux<EncryptedEnvelope<T>>
    fun fetchByDevice(deviceId: Key<T>, afterSeq: Long, limit: Int): Flux<EncryptedEnvelope<T>>
    fun ackDelivery(deviceId: Key<T>, conversationId: Key<T>, seq: Long): Mono<Void>
}

/**
 * Conversation cursor service — per-conversation sequence allocation.
 * Allocates seq inside the same transaction that writes the message and device inbox.
 * Linearizable, no gaps for committed messages.
 */
interface ConversationSeqService<T> {
    fun nextSeq(conversationId: Key<T>): Mono<Long>
    fun getCursor(conversationId: Key<T>): Mono<ConversationCursor<T>>
    fun initCursor(conversationId: Key<T>): Mono<Void>
}

/**
 * Conversation epoch service — server-visible membership boundaries.
 * Prevents history leaks across membership changes.
 */
interface ConversationEpochService<T> {
    fun startEpoch(conversationId: Key<T>): Mono<ConversationEpoch<T>>
    fun endEpoch(epochId: Key<T>): Mono<Void>
    fun getCurrentEpoch(conversationId: Key<T>): Mono<ConversationEpoch<T>>
    fun getEpochs(conversationId: Key<T>): Flux<ConversationEpoch<T>>
}

/**
 * Franking service — generates and verifies server-side franking tags.
 * Tags bind (conversationId, seq, senderDeviceId, messageKind, ciphertext hash).
 * Used for abuse reporting: the reporter can prove a message existed without revealing plaintext.
 */
interface FrankingService<T> {
    fun generateTag(
        conversationId: Key<T>,
        seq: Long,
        senderDeviceId: Key<T>,
        messageKind: MessageKind,
        ciphertextHash: ByteArray
    ): Mono<FrankingTag<T>>

    fun verifyTag(tag: FrankingTag<T>, ciphertextHash: ByteArray): Mono<Boolean>
    fun rotateKey(): Mono<Int>
}

/**
 * Presence service — ephemeral presence tracking via heartbeat TTL.
 * Never persisted durably. Heartbeat keys expire; offline is derived from absence.
 */
interface PresenceService<T> {
    fun heartbeat(userId: Key<T>, deviceId: Key<T>): Mono<Void>
    fun setState(userId: Key<T>, deviceId: Key<T>, state: PresenceState): Mono<Void>
    fun getPresence(userId: Key<T>): Flux<Presence<T>>
    fun getOnlineDevices(conversationId: Key<T>): Flux<Key<T>>
    fun subscribePresence(userId: Key<T>): Flux<Presence<T>>
}
