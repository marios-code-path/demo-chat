package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The redis node id lease.
 *
 * The key carries the key type, because a cassandra deployment already
 * separates key types by keyspace. A long deployment and a uuid deployment
 * on one redis may both hold node id 7. Their value spaces do not intersect.
 *
 * `PX` makes the redis server the clock. No application clock enters the
 * decision.
 */
class RedisNodeIdClaimStore(
    private val template: ReactiveStringRedisTemplate,
    private val keyType: String
) : NodeIdClaimStore {

    override val backendName: String = "redis"

    override val scope: String = "redis store for key type $keyType"

    private fun key(nodeId: NodeId): String = "chat:nodeclaim:$keyType:${nodeId.value}"

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        attempt(nodeId, owner, ttl)
            // The holder vanished between the write and the read. Try once more.
            .switchIfEmpty(attempt(nodeId, owner, ttl))
            .switchIfEmpty(
                Mono.error(
                    IllegalStateException(
                        "The $scope denied app.nodeid=${nodeId.value} twice and named no holder."
                    )
                )
            )

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        run(renewScript, nodeId, owner, ttl.toMillis().toString())

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        run(releaseScript, nodeId, owner, "0").then()

    private fun attempt(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        template.opsForValue().setIfAbsent(key(nodeId), owner, ttl)
            .flatMap { taken ->
                if (taken) Mono.just(ClaimResult.Granted as ClaimResult)
                // A diagnostic read. A stale value costs message quality only.
                else template.opsForValue().get(key(nodeId))
                    .map { holder -> ClaimResult.Denied(holder) as ClaimResult }
            }

    private fun run(
        script: RedisScript<String>,
        nodeId: NodeId,
        owner: String,
        millis: String
    ): Mono<ClaimResult> =
        template.execute(script, listOf(key(nodeId)), listOf(owner, millis))
            .next()
            .map { reply ->
                when {
                    reply == "granted" -> ClaimResult.Granted
                    reply == "lost" -> ClaimResult.Lost
                    reply.startsWith("denied:") -> ClaimResult.Denied(reply.removePrefix("denied:"))
                    else -> throw IllegalStateException(
                        "The $scope returned an unknown claim reply: $reply"
                    )
                }
            }

    companion object {
        // Lua gives the compare and set that plain commands cannot. The reply
        // is one string, because ReactiveStringRedisTemplate carries a string
        // serializer and a mixed Lua array would need a mixed result type.
        private val renewScript: RedisScript<String> = RedisScript.of(
            """
            local v = redis.call('GET', KEYS[1])
            if v == false then return 'lost' end
            if v == ARGV[1] then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return 'granted'
            end
            return 'denied:' .. v
            """.trimIndent(),
            String::class.java
        )

        private val releaseScript: RedisScript<String> = RedisScript.of(
            """
            local v = redis.call('GET', KEYS[1])
            if v == false then return 'lost' end
            if v == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 'granted'
            end
            return 'denied:' .. v
            """.trimIndent(),
            String::class.java
        )
    }
}
