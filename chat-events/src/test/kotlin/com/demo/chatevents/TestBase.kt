package com.demo.chatevents

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.util.*

fun textMessageAssertion(msg: com.demo.chat.domain.Message<Any, Any>) {
    MatcherAssert
            .assertThat("A Text Message should have property state", msg,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("value", Matchers.not(Matchers.isEmptyOrNullString())),
                            Matchers.hasProperty("key",
                                    Matchers
                                            .allOf(Matchers.notNullValue(),
                                                    Matchers.hasProperty("id"),
                                                    Matchers.hasProperty("dest"))
                            ))
            )
}

fun testRoomId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")//UUID.randomUUID()

fun testUserId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

fun randomUserId(): UUID {
    val lastDigit = Integer.toHexString(Random().nextInt(16))
    return UUID.fromString("ecb2cb88-5dd1-44c3-b818-13373000000$lastDigit")
}

fun randomText() =
        "Text ${Random().nextLong()}"

fun testTemplate(cf: ReactiveRedisConnectionFactory, objectMapper: ObjectMapper): ReactiveRedisTemplate<String, TestEntity> {
    val keys = StringRedisSerializer()
    val values = Jackson2JsonRedisSerializer(TestEntity::class.java)
    values.setObjectMapper(objectMapper)           // KOTLIN USERS : use setObjectMapper!

    val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, TestEntity> =
            RedisSerializationContext.newSerializationContext(keys)

    val hashValues = Jackson2JsonRedisSerializer(TestEntity::class.java)
    //hashValues.setObjectMapper(objectMapper())

    builder.key(keys)
    builder.value(values)
    builder.hashKey(keys)
    builder.hashValue(hashValues)

    return ReactiveRedisTemplate(cf, builder.build())
}