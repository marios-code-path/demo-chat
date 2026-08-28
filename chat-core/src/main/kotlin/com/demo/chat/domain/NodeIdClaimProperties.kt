package com.demo.chat.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The lease timings.
 *
 * The constructor takes nullable parameters and applies the defaults in the
 * body. It declares no Kotlin default arguments on purpose. A default
 * argument makes Kotlin emit a synthetic constructor, and Spring Boot then
 * has more than one constructor to choose from. The same trap already bit
 * `ConfigurationPropertiesRedisTopics`.
 */
@ConfigurationProperties(prefix = "app.nodeid.claim")
class NodeIdClaimProperties(
    ttl: Duration?,
    renewInterval: Duration?,
    safetyMargin: Duration?,
    operationTimeout: Duration?
) {
    val ttl: Duration = ttl ?: Duration.ofSeconds(30)
    val renewInterval: Duration = renewInterval ?: Duration.ofSeconds(10)
    val safetyMargin: Duration = safetyMargin ?: Duration.ofSeconds(5)
    val operationTimeout: Duration = operationTimeout ?: Duration.ofSeconds(5)

    /** The process closes this long after the last successful claim or renew. */
    val closeDeadline: Duration = this.ttl.minus(this.safetyMargin)

    init {
        require(this.ttl >= Duration.ofSeconds(1)) {
            "app.nodeid.claim.ttl must be at least 1s. Got: ${this.ttl}"
        }
        // Cassandra applies a TTL in whole seconds. A fractional value would
        // round on one backend and not on the other.
        require(this.ttl.nano == 0) {
            "app.nodeid.claim.ttl must use whole seconds. Got: ${this.ttl}"
        }
        require(!this.renewInterval.isZero && !this.renewInterval.isNegative) {
            "app.nodeid.claim.renew-interval must be greater than zero. Got: ${this.renewInterval}"
        }
        require(this.renewInterval <= this.ttl.dividedBy(3)) {
            "app.nodeid.claim.renew-interval must be at most ttl / 3. " +
                "ttl is ${this.ttl}. Got: ${this.renewInterval}"
        }
        require(!this.safetyMargin.isZero && !this.safetyMargin.isNegative) {
            "app.nodeid.claim.safety-margin must be greater than zero. Got: ${this.safetyMargin}"
        }
        require(this.safetyMargin < this.ttl) {
            "app.nodeid.claim.safety-margin must be less than ttl. " +
                "ttl is ${this.ttl}. Got: ${this.safetyMargin}"
        }
        // A blocked call must not consume the next renewal slot.
        require(this.operationTimeout < this.renewInterval) {
            "app.nodeid.claim.operation-timeout must be less than renew-interval. " +
                "renew-interval is ${this.renewInterval}. Got: ${this.operationTimeout}"
        }
        require(this.closeDeadline > this.renewInterval) {
            "ttl minus app.nodeid.claim.safety-margin must be greater than renew-interval. " +
                "The deadline is ${this.closeDeadline}. renew-interval is ${this.renewInterval}."
        }
    }
}
