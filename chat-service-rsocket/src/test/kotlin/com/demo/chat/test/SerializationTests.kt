package com.demo.chat.test

import com.demo.chat.TestUUIDKey
import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.module
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.text.SimpleDateFormat
import java.util.*


@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TestKey")
interface TestKey : Key<UUID> {
    override val id: UUID

    companion object Factory {
        @JvmStatic
        fun create(eid: UUID): UUIDKey = object : UUIDKey {
            override val id: UUID
                @JsonProperty("eid") get() = eid
        }
    }
}

data class ETestKey(@JsonProperty("eid") override val id: UUID) : TestKey

// If you dont register module explicitly, then use this way:
// after putting @JsonDeserialize(`as` = ETestKey::class) on the
// interface class
//data class ETestKey @JsonCreator constructor
//(@JsonProperty("eid") override val id: UUID) : TestKey


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(JacksonAutoConfiguration::class, SerializationTests.SerializationConfiguration::class)
class SerializationTests {

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `serialization tests`() {
        mapper.registeredModuleIds.forEach {
            System.out.println("MODULE: $it")
        }

        val randomEventKey = Key.eventKey(UUID.randomUUID())

        val data = mapper.writeValueAsString(randomEventKey)

        val obj = mapper.readValue(data, UUIDKey::class.java)

    }

    class SerializationConfiguration {

        @Bean
        fun mapper(): ObjectMapper {
            return ObjectMapper().apply {
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                registerModule(KotlinModule())
                setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            }
        }

        @Bean
        fun eventKeyModule() = module("EVENTKEY", UUIDKey::class.java, TestUUIDKey::class.java)

        fun jacksonBuilder(): Jackson2ObjectMapperBuilder {
            val b = Jackson2ObjectMapperBuilder()
            println("AM I IN MODULE LAND ? ? ? ? ")
            b.indentOutput(true).dateFormat(SimpleDateFormat("yyyy-MM-dd"))
            b.modules(
                    module("TESTKEY", TestKey::class.java, ETestKey::class.java),
                    module("EVENTKEY", UUIDKey::class.java, TestUUIDKey::class.java)
            )
            return b
        }
    }
}
