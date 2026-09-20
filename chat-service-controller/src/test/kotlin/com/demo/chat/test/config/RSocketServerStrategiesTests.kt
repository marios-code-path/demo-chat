package com.demo.chat.test.config

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.rsocket.RSocketServerConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.messaging.rsocket.RSocketStrategies
import java.nio.charset.StandardCharsets

/**
 * The production RSocket server strategies, driven through the production bean.
 *
 * **This test exists because the module tests do not reach this bean.** The
 * tests in chat-client-rsocket use their own test server configuration, so
 * they prove the shape of the repair and not the production customizer. This
 * test applies `RSocketServerConfiguration.rSocketStrategiesCustomizer`
 * itself.
 *
 * The claim it carries is narrow and stated on purpose:
 *
 * | Proven here | Not proven here |
 * |---|---|
 * | The production customizer registers a decoder that reads a `Key` | That a deployment injects the domain module into it |
 * | That decoder sits at position 0 | That it beats the decoder Boot auto-configures |
 * | | The CBOR path |
 *
 * **The second column matters.** `RSocketStrategies.builder()` answers four
 * default decoders here, and none of them reads JSON. So this test cannot
 * show that the domain decoder beats a competing Jackson decoder. The
 * measurement that shows that is in chat-client-rsocket, where Boot
 * auto-configures one: removal of the position 0 insert gives 16 failures
 * in 9 classes there.
 *
 * **There is no CBOR path to cover.** Its one reader was a spike that
 * derived a target from the payload, and it is removed. Authorization goes
 * through `SpringSecurityAccessBrokerService`. See CHAT-bgsqwjph.
 */
class RSocketServerStrategiesTests {

    private val strategies: RSocketStrategies = RSocketStrategies.builder()
        .let { builder ->
            RSocketServerConfiguration<Long>()
                .rSocketStrategiesCustomizer(listOf(ChatJackson3Modules().chatJackson3Module()))
                .customize(builder)
            builder.build()
        }

    private val bufferFactory = DefaultDataBufferFactory()

    private fun decode(json: String, target: ResolvableType): Any? {
        val buffer = bufferFactory.wrap(json.toByteArray(StandardCharsets.UTF_8))

        // The first decoder that matches a type is the one Spring uses, so the
        // search here repeats the production selection rather than naming a
        // decoder by index.
        val decoder = strategies.decoders().first {
            it.canDecode(target, MediaType.APPLICATION_JSON)
        }

        return decoder.decode(buffer, target, MediaType.APPLICATION_JSON, null)
    }

    @Test
    fun `the customizer puts the domain decoder at position 0`() {
        val target = ResolvableType.forClass(Key::class.java)

        val winner = strategies.decoders().first {
            it.canDecode(target, MediaType.APPLICATION_JSON)
        }

        // An appended decoder never runs, so position is the whole point of
        // the repair. The four default decoders here read no JSON, so this
        // states the position and claims nothing about a competing decoder.
        assertThat(winner)
            .describedAs("the domain decoder is the first decoder")
            .isSameAs(strategies.decoders().first())
            .isInstanceOf(JacksonJsonDecoder::class.java)
    }

    @Test
    fun `the production customizer decodes a Key`() {
        // The measured wire shape. Key carries its own @JsonTypeInfo wrapper.
        val decoded = decode(
            """{"key":{"empty":false,"id":1001}}""",
            ResolvableType.forClass(Key::class.java),
        )

        assertThat(decoded)
            .describedAs("a Key request payload reaches the controller as a Key")
            .isInstanceOf(Key::class.java)

        val key = decoded as Key<*>
        assertThat(key.id).isEqualTo(1001L)
        assertThat(key)
            .describedAs("a key with no from and no dest stays a Key")
            .isNotInstanceOf(MessageKey::class.java)
    }

    @Test
    fun `the production customizer decodes a MessageKey`() {
        val decoded = decode(
            """{"key":{"empty":false,"id":7,"from":10,"dest":20}}""",
            ResolvableType.forClass(Key::class.java),
        )

        assertThat(decoded)
            .describedAs("from and dest make this a MessageKey")
            .isInstanceOf(MessageKey::class.java)

        val key = decoded as MessageKey<*>
        assertThat(key.id).isEqualTo(7L)
        assertThat(key.from).isEqualTo(10L)
        assertThat(key.dest).isEqualTo(20L)
    }
}
