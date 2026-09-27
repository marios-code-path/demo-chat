package com.demo.chat.test.resolve

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.controller.resolve.KeyDomain
import com.demo.chat.controller.resolve.Verified
import com.demo.chat.controller.resolve.VerifiedKeyArgumentResolver
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.core.MethodParameter
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets

/**
 * The messaging resolver decodes a `Key` payload and verifies it. See
 * `CHAT-avduuqwp`, D1.
 */
class VerifiedKeyArgumentResolverTests {
    private val roots = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(roots)

    private val mapper = JsonMapper.builder().addModule(ChatJackson3Modules().chatJackson3Module()).build()

    private val resolver = VerifiedKeyArgumentResolver(
        listOf(JacksonJsonDecoder(mapper)),
        StaticListableBeanFactory(mapOf("verifier" to KeyVerifier(keys, roots))).getBeanProvider(KeyVerifier::class.java),
    )

    /** The handler signatures that the resolver reads. */
    @KeyDomain(ChatDomain.MESSAGE_TOPIC)
    @Suppress("UNUSED_PARAMETER")
    class Probe {
        fun user(@Verified(ChatDomain.USER) key: VerifiedKey<Long>) = Unit
        fun classDomain(@Verified key: VerifiedKey<Long>) = Unit
        fun anyDomain(@Verified(anyDomain = true) key: VerifiedKey<Long>) = Unit
        fun plain(key: Key<Long>) = Unit
    }

    private fun parameter(method: String): MethodParameter =
        MethodParameter(Probe::class.java.methods.single { it.name == method }, 0)

    private fun resolve(method: String, key: Key<Long>) = resolver.resolveArgument(
        parameter(method),
        MessageBuilder
            .withPayload(DefaultDataBufferFactory.sharedInstance.wrap(mapper.writeValueAsString(key).toByteArray(StandardCharsets.UTF_8)))
            .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
            .build(),
    )

    @Test
    fun `it supports a Verified VerifiedKey parameter alone`() {
        assertThat(resolver.supportsParameter(parameter("user"))).isTrue()
        assertThat(resolver.supportsParameter(parameter("plain"))).isFalse()
    }

    @Test
    fun `a registered key verifies`() {
        val user = keys.register(4401L, ChatDomain.USER)

        StepVerifier.create(resolve("user", user))
            .assertNext { assertThat((it as VerifiedKey<*>).key).isEqualTo(user) }
            .verifyComplete()
    }

    @Test
    fun `an unknown key is refused`() {
        StepVerifier.create(resolve("user", Key.of(424246L, roots.of(ChatDomain.USER).id)))
            .verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `a forged root is refused`() {
        val user = keys.register(4402L, ChatDomain.USER)

        StepVerifier.create(resolve("anyDomain", Key.of(user.id, roots.of(ChatDomain.MESSAGE).id)))
            .verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `a key of another domain is refused`() {
        val message = keys.register(4403L, ChatDomain.MESSAGE)

        StepVerifier.create(resolve("user", message)).verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `no domain verifies in the KeyDomain of the class`() {
        val room = keys.register(4404L, ChatDomain.MESSAGE_TOPIC)
        val user = keys.register(4405L, ChatDomain.USER)

        StepVerifier.create(resolve("classDomain", room))
            .assertNext { assertThat((it as VerifiedKey<*>).key).isEqualTo(room) }
            .verifyComplete()
        StepVerifier.create(resolve("classDomain", user)).verifyError(KeyVerificationException::class.java)
    }
}
