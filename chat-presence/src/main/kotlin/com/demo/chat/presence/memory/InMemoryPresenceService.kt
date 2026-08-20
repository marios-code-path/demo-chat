package com.demo.chat.presence.memory

import com.demo.chat.domain.*
import com.demo.chat.service.core.PresenceService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory presence service — tracks per-device presence via heartbeat timestamps.
 *
 * Heartbeat TTL is simulated: if lastHeartbeat is older than the threshold (default 30s),
 * the device is considered OFFLINE. This mirrors the Redis TTL key approach described
 * in the plan — production would use Redis EXPIRE keys instead of polling.
 *
 * Presence is ephemeral — never persisted durably. State is derived from heartbeat freshness.
 */
class InMemoryPresenceService<T>(
    private val heartbeatTimeoutMs: Long = 30_000L
) : PresenceService<T> {

    private val presenceState = ConcurrentHashMap<T, Presence<T>>()
    private val deviceToConversation = ConcurrentHashMap<T, MutableList<Key<T>>>()
    private val presenceSinks = ConcurrentHashMap<T, Sinks.Many<Presence<T>>>()

    override fun heartbeat(userId: Key<T>, deviceId: Key<T>): Mono<Void> =
        Mono.fromRunnable<Void> {
            val existing = presenceState[deviceId.id]
            val newState = if (existing?.state == PresenceState.AWAY) PresenceState.AWAY else PresenceState.ONLINE
            val presence = Presence.create(userId, deviceId, newState)
            presenceState[deviceId.id] = presence
            emitPresence(userId, presence)
        }.then()

    override fun setState(userId: Key<T>, deviceId: Key<T>, state: PresenceState): Mono<Void> =
        Mono.fromRunnable<Void> {
            val presence = Presence.create(userId, deviceId, state)
            presenceState[deviceId.id] = presence
            emitPresence(userId, presence)
        }.then()

    override fun getPresence(userId: Key<T>): Flux<Presence<T>> =
        Flux.fromIterable(
            presenceState.values.filter { it.userId == userId }
        )

    override fun getOnlineDevices(conversationId: Key<T>): Flux<Key<T>> =
        Flux.fromIterable(
            presenceState.values
                .filter { isOnline(it) }
                .map { it.deviceId }
        )

    override fun subscribePresence(userId: Key<T>): Flux<Presence<T>> =
        presenceSinks.computeIfAbsent(userId.id) {
            Sinks.many().multicast().onBackpressureBuffer()
        }.asFlux()

    fun registerDeviceToConversation(deviceId: Key<T>, conversationId: Key<T>) {
        deviceToConversation.computeIfAbsent(deviceId.id) { mutableListOf() }
            .add(conversationId)
    }

    private fun isOnline(presence: Presence<T>): Boolean {
        val now = System.currentTimeMillis()
        return presence.state == PresenceState.ONLINE &&
            (now - presence.lastHeartbeat) < heartbeatTimeoutMs
    }

    private fun emitPresence(userId: Key<T>, presence: Presence<T>) {
        presenceSinks.computeIfAbsent(userId.id) {
            Sinks.many().multicast().onBackpressureBuffer()
        }.tryEmitNext(presence)
    }
}
