package com.demo.chat.config.deploy.e2ee

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/**
 * E2EE deployment configuration properties.
 * Activated when the e2ee profile is included.
 *
 * app.service.crypto.backend=memory   → In-memory crypto services (default for dev)
 * app.service.presence.backend=memory → In-memory presence (default for dev)
 *
 * Production would use:
 * app.service.crypto.backend=redis    → Redis-backed pre-key pool + device inbox
 * app.service.presence.backend=redis  → Redis TTL heartbeats + pub/sub presence fan-out
 */
@ConfigurationProperties(prefix = "app.service.e2ee")
data class E2EEDeployProperties @ConstructorBinding constructor(
    val enabled: Boolean = false,
    val cryptoBackend: String = "memory",
    val presenceBackend: String = "memory",
    val heartbeatTimeoutMs: Long = 30_000,
    val preKeyRefillThreshold: Int = 5,
    val messageRetentionDays: Int = 365,
    val frankingKeyRotationDays: Int = 30,
    val historyVisibility: String = "SINCE_JOIN"
)
