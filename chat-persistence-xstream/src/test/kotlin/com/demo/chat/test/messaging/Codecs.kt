package com.demo.chat.test.messaging

import com.demo.chat.codec.Decoder
import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.messaging.KeyConfiguration
import com.demo.chat.service.impl.memory.messaging.PubSubTopicExchangeRedisStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration
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