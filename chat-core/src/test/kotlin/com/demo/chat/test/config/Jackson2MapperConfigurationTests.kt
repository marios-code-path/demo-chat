package com.demo.chat.test.config

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.config.Jackson2MapperConfiguration
import com.demo.chat.domain.Key
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The Jackson 2 mapper carries the chat domain modules.
 *
 * **This seam replaced a per backend customizer.** Spring Boot 4 removed
 * `Jackson2ObjectMapperBuilderCustomizer`, and `chat-persistence-redis` used
 * one to install the same modules that `DefaultChatJacksonModules` already
 * publishes as beans. CHAT-wemeelbz removed that customizer, and this test is
 * what proves the remaining path still installs them.
 *
 * Without the module beans the mapper reads a `Key` as a bean and fails,
 * because `KeyDeserializer` is what teaches it the shape.
 */
class Jackson2MapperConfigurationTests {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            DefaultChatJacksonModules::class.java,
            Jackson2MapperConfiguration::class.java,
        )

    @Test
    fun `the named mapper exists and reads a chat domain type`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()

            val mapper = context.getBean(JACKSON_2_OBJECT_MAPPER, ObjectMapper::class.java)

            // A round trip, rather than a hand written document. Key carries
            // its own type wrapper, so a literal here would pin the wrapper
            // shape by accident and fail for the wrong reason.
            val json = mapper.writeValueAsString(Key.funKey(42L))
            val key = mapper.readValue(json, Key::class.java)

            assertThat(key.id)
                .describedAs("the key module must read the id back")
                .isEqualTo(42L)
        }
    }

    @Test
    fun `the mapper installs every chat domain module`() {
        // Seven module beans, one per domain type. A count proves that the
        // collection reached the builder, and it fails if a later change
        // stops publishing them or stops collecting them.
        runner.run { context ->
            val published = context.getBeansOfType(
                com.fasterxml.jackson.databind.Module::class.java
            )

            assertThat(published)
                .describedAs("DefaultChatJacksonModules publishes the domain modules")
                .hasSize(7)

            val mapper = context.getBean(JACKSON_2_OBJECT_MAPPER, ObjectMapper::class.java)
            val installed = mapper.registeredModuleIds.map { it.toString() }

            assertThat(installed)
                .describedAs("every published module reaches the mapper")
                .containsAll(published.values.map { it.typeId.toString() })
        }
    }
}
