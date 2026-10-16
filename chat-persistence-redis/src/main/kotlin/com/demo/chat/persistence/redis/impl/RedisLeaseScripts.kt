package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import org.springframework.data.redis.core.script.RedisScript

/** The compare and set scripts of the Redis leases. The node id and service leases share them. */
object RedisLeaseScripts {
    // Lua gives the compare and set that plain commands cannot. The reply is
    // one string, because ReactiveStringRedisTemplate carries a string
    // serializer and a mixed Lua array would need a mixed result type.
    val renew: RedisScript<String> = RedisScript.of(
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

    val release: RedisScript<String> = RedisScript.of(
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

    fun result(reply: String, scope: String): ClaimResult = when {
        reply == "granted" -> ClaimResult.Granted
        reply == "lost" -> ClaimResult.Lost
        reply.startsWith("denied:") -> ClaimResult.Denied(reply.removePrefix("denied:"))
        else -> throw IllegalStateException("The $scope returned an unknown claim reply: $reply")
    }
}
