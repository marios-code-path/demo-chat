package com.demo.chat.test

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.Key
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver
import com.fasterxml.jackson.databind.module.SimpleModule
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
interface TestKey : Key<UUID> {
    override val id: UUID

    companion object Factory {
        @JvmStatic
        fun create(eid: UUID): Key<UUID> = object : Key<UUID> {
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
    fun `serialization tests dont except`() {
        val randomEventKey = Key.funKey(UUID.randomUUID())

        val data = mapper.writeValueAsString(randomEventKey)

        mapper.readValue(data, TestUUIDKey::class.java)
    }

    class SerializationConfiguration {

        @Bean
        fun mapper(): ObjectMapper {
            return ObjectMapper().apply {
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                registerModule(KotlinModule())
                val module = JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)
                registerModules(
                        module.keyModule(),
                        module("TESTKEY", TestKey::class.java, ETestKey::class.java)
                )
                setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            }
        }

        // Register this abstract module to let the app know when it sees a Interface type, which
        // concrete type to use on the way out.
        fun <T> module(name: String, iface: Class<T>, concrete: Class<out T>) = SimpleModule("CustomModel$name", Version.unknownVersion())
                .apply { setAbstractTypes(SimpleAbstractTypeResolver().apply { addMapping(iface, concrete) }) }

        // old but useful to explain where this could potentially happen where autoscanning is not
        // used.
        fun jacksonBuilder(): Jackson2ObjectMapperBuilder {
            val b = Jackson2ObjectMapperBuilder()
            b.indentOutput(true).dateFormat(SimpleDateFormat("yyyy-MM-dd"))
            b.modules(
                    module("TESTKEY", TestKey::class.java, ETestKey::class.java),
                    module("EVENTKEY", Key::class.java, TestUUIDKey::class.java)
            )
            return b
        }
    }
}