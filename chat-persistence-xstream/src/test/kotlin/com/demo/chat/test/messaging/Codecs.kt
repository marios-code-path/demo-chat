package com.demo.chat.test.messaging

import com.demo.chat.codec.Decoder
import com.demo.chat.config.ConfigurationPropertiesRedis
import java.util.*

class StringUUIDKeyDecoder : Decoder<String, UUID> {
    override fun decode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDKeyStringEncoder : Decoder<UUID, String> {
    override fun decode(record: UUID): String {
        return record.toString()
    }
}

object TestConfigurationPropertiesRedisCluster : ConfigurationPropertiesRedis {
    override val port: Int = (System.getenv("REDIS_TEST_PORT") ?: "58088").toInt()
    override val host: String = System.getenv("REDIS_TEST_HOST") ?: "127.0.0.1"
}