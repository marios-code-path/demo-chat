package com.demo.chat.service.command

import com.demo.chat.domain.NoEffectRefusal
import com.demo.chat.domain.command.UncertainOutcomeException
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

enum class FailureClass { DEFINITIVE, TRANSIENT, UNCERTAIN }

/**
 * Retry and recovery values for one backend. Decision 12 of the spec.
 *
 * [initialAttempts] includes the first attempt. Reactor counts retries only,
 * so the runtime passes [retriesAfterFirst] to `Retry.backoff`.
 *
 * Only a [NoEffectRefusal] is definitive. An unknown error can follow an
 * effect, so it is uncertain. [attemptTimeout] marks a running attempt as
 * uncertain. It never cancels the attempt, because a cancel does not prove
 * that the backend work stopped. All values are starting values, not
 * production measurements.
 */
data class BackendExecutionPolicy(
    val version: Int = 1,
    val initialAttempts: Int = 5,
    val firstBackoff: Duration = Duration.ofMillis(100),
    val recoveryInterval: Duration = Duration.ofSeconds(30),
    val attemptTimeout: Duration = Duration.ofSeconds(30),
) {
    init {
        require(initialAttempts >= 1) { "A policy needs at least one attempt. It has $initialAttempts." }
        require(!recoveryInterval.isNegative && !recoveryInterval.isZero) { "The recovery interval must be positive." }
    }

    val retriesAfterFirst: Long get() = (initialAttempts - 1).toLong()

    /** The first matching rule wins. The rules read the whole cause chain. */
    fun classify(error: Throwable): FailureClass {
        val chain = generateSequence(error) { e -> e.cause?.takeIf { it !== e } }.take(MAX_CHAIN).toList()
        return when {
            chain.any { it is UncertainOutcomeException } -> FailureClass.UNCERTAIN
            chain.any { it is NoEffectRefusal } -> FailureClass.DEFINITIVE
            chain.any { it is TimeoutException || it is IOException || it.javaClass.name in TRANSIENT_TYPES } ->
                FailureClass.TRANSIENT
            else -> FailureClass.UNCERTAIN
        }
    }

    companion object {
        private const val MAX_CHAIN = 16

        /** Driver types that `chat-core` cannot import. The runtime matches them by name. */
        val TRANSIENT_TYPES = setOf(
            "com.datastax.oss.driver.api.core.DriverTimeoutException",
            "com.datastax.oss.driver.api.core.AllNodesFailedException",
            "com.datastax.oss.driver.api.core.NoNodeAvailableException",
            "com.datastax.oss.driver.api.core.servererrors.WriteTimeoutException",
            "io.lettuce.core.RedisConnectionException",
            "io.lettuce.core.RedisCommandTimeoutException",
            "org.springframework.dao.QueryTimeoutException",
            "org.springframework.data.redis.RedisConnectionFailureException",
        )
    }
}
