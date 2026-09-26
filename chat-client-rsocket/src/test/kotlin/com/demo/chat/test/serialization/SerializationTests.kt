package com.demo.chat.test.serialization

import com.demo.chat.config.DefaultChatJacksonModules

import com.demo.chat.test.key.TestKeys

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.Key
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.Version
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.config.Jackson2MapperConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.text.SimpleDateFormat
import java.util.*

// The custom key types that stood here are removed. Only SimpleKey, EmptyKey
// and SimpleMessageKey implement Key. See CHAT-avduuqwp, A21.

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    DefaultChatJacksonModules::class,
    Jackson2MapperConfiguration::class,
    JacksonAutoConfiguration::class,
    SerializationTests.SerializationConfiguration::class,
)
class SerializationTests {

    /**
     * The Jackson 2 mapper that this test measures.
     *
     * **Boot 3 chose this mapper by accident, and Boot 4 exposed that.** The
     * `@Import` below names `JacksonAutoConfiguration` before the nested fixture.
     * So Boot 3 evaluated `@ConditionalOnMissingBean` with no `ObjectMapper`
     * present, registered `jacksonObjectMapper`, and marked it `@Primary`. The
     * primary bean won this injection, and the nested `mapper()` bean never
     * served the assertion.
     *
     * **Boot 4 publishes no Jackson 2 `ObjectMapper`.** Its
     * `JacksonAutoConfiguration` builds a Jackson 3 `JsonMapper`. The nested
     * `mapper()` bean then became the only candidate, and it answered
     * `{"key":{}}`. That mapper sets `GETTER` visibility to `NONE`, and
     * `Key.funKey` carries `id` on a getter with no field, so the id never
     * reached the wire.
     *
     * The qualifier names the Jackson 2 mapper of this repository. It restores
     * what this test measured before, and it states the choice rather than
     * inheriting it. See CHAT-sxydbspq.
     */
    @Autowired
    @Qualifier(JACKSON_2_OBJECT_MAPPER)
    lateinit var mapper: ObjectMapper

    @Test
    fun `a key round trips through the key module`() {
        val randomEventKey = TestKeys.key(UUID.randomUUID())

        val data = mapper.writeValueAsString(randomEventKey)

        org.assertj.core.api.Assertions.assertThat(mapper.readValue(data, Key::class.java)).isEqualTo(randomEventKey)
    }

    class SerializationConfiguration {

        @Bean
        fun mapper(): ObjectMapper {
            return ObjectMapper().apply {
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                registerModule(KotlinModule.Builder().build())
                val module = JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)
                registerModules(module.keyModule())
                setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            }
        }
    }
}