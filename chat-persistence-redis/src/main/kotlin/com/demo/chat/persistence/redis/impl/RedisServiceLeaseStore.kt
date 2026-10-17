package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ServiceLeaseStore
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The Redis store service lease. See `CHAT-lswjobhz`.
 *
 * The key carries the key type, as the node id lease does. `PX` makes the
 * Redis server the clock.
 */
class RedisServiceLeaseStore(
    private val template: ReactiveStringRedisTemplate,
    private val keyType: String,
) : ServiceLeaseStore {

    override val backendName: String = "redis"

    override val scope: String = "redis store for key type $keyType"

    private fun key(name: String): String = "chat:lease:$keyType:$name"

    override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        attempt(name, owner, ttl)
            // The holder vanished between the write and the read. Try once more.
            .switchIfEmpty(attempt(name, owner, ttl))
            .switchIfEmpty(Mono.error(IllegalStateException("The $scope denied $name twice and named no holder.")))

    override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        run(RedisLeaseScripts.renew, name, owner, ttl.toMillis().toString())

    override fun release(name: String, owner: String): Mono<Void> =
        run(RedisLeaseScripts.release, name, owner, "0").then()

    private fun attempt(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        template.opsForValue().setIfAbsent(key(name), owner, ttl).flatMap { taken ->
            if (taken) Mono.just(ClaimResult.Granted as ClaimResult)
            else template.opsForValue().get(key(name)).map { holder -> ClaimResult.Denied(holder) as ClaimResult }
        }

    private fun run(script: RedisScript<String>, name: String, owner: String, millis: String): Mono<ClaimResult> =
        template.execute(script, listOf(key(name)), listOf(owner, millis)).next()
            .map { RedisLeaseScripts.result(it, scope) }
}
