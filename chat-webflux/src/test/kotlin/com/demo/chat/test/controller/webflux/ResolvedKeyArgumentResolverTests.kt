package com.demo.chat.test.controller.webflux

import com.demo.chat.controller.webflux.resolve.DomainScoped
import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.controller.webflux.resolve.ResolvedKeyArgumentResolver
import com.demo.chat.domain.KeyInputException
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.core.MethodParameter
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.HandlerMapping
import reactor.test.StepVerifier

/**
 * The web resolver turns a path id into a `VerifiedKey`, or it refuses. See
 * `CHAT-avduuqwp`, D2.
 */
class ResolvedKeyArgumentResolverTests {
    private val roots = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(roots)

    private val resolver = ResolvedKeyArgumentResolver(
        StaticListableBeanFactory(mapOf("verifier" to KeyVerifier(keys, roots))).getBeanProvider(KeyVerifier::class.java),
        StaticListableBeanFactory(mapOf("typeUtil" to LongUtil())).getBeanProvider(TypeUtil::class.java),
    )

    /** The handler signatures that the resolver reads. */
    @Suppress("UNUSED_PARAMETER")
    class Probe : DomainScoped {
        override fun domain(): ChatDomain = ChatDomain.MESSAGE_TOPIC
        fun user(@Resolved(ChatDomain.USER) id: VerifiedKey<Long>) = Unit
        fun handlerDomain(@Resolved id: VerifiedKey<Long>) = Unit
        fun anyDomain(@Resolved(anyDomain = true) id: VerifiedKey<Long>) = Unit
        fun plain(id: String) = Unit
    }

    private fun parameter(method: String): MethodParameter =
        MethodParameter(Probe::class.java.methods.single { it.name == method }, 0)

    private fun resolve(method: String, segment: String) =
        MockServerWebExchange.from(MockServerHttpRequest.get("/x/$segment")).let { exchange ->
            exchange.attributes[HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE] = mapOf("id" to segment)
            exchange.attributes[HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE] =
                HandlerMethod(Probe(), Probe::class.java.methods.single { it.name == method })
            resolver.resolveArgument(parameter(method), BindingContext(), exchange)
        }

    @Test
    fun `it supports a Resolved VerifiedKey parameter alone`() {
        assertThat(resolver.supportsParameter(parameter("user"))).isTrue()
        assertThat(resolver.supportsParameter(parameter("plain"))).isFalse()
    }

    @Test
    fun `a registered id resolves in its domain`() {
        val user = keys.register(4201L, ChatDomain.USER)

        StepVerifier.create(resolve("user", "4201"))
            .assertNext { assertThat((it as VerifiedKey<*>).key).isEqualTo(user) }
            .verifyComplete()
    }

    @Test
    fun `an unknown id is refused`() {
        StepVerifier.create(resolve("user", "424242")).verifyError(KeyVerificationException::class.java)
    }

    // A path carries no root, so a forged root arrives as an id of another domain.
    @Test
    fun `an id of another domain is refused`() {
        keys.register(4202L, ChatDomain.MESSAGE)

        StepVerifier.create(resolve("user", "4202")).verifyError(KeyVerificationException::class.java)
    }

    // An id that does not convert exactly is an input error, not an unknown id.
    @Test
    fun `a malformed, fractional, or overflowing id is an input error`() {
        StepVerifier.create(resolve("user", "not-a-number")).verifyError(KeyInputException::class.java)
        StepVerifier.create(resolve("user", "42.9")).verifyError(KeyInputException::class.java)
        StepVerifier.create(resolve("user", "18446744073709551658")).verifyError(KeyInputException::class.java)
    }

    @Test
    fun `no domain resolves in the domain of the handler`() {
        val room = keys.register(4203L, ChatDomain.MESSAGE_TOPIC)
        keys.register(4204L, ChatDomain.USER)

        StepVerifier.create(resolve("handlerDomain", "4203"))
            .assertNext { assertThat((it as VerifiedKey<*>).key).isEqualTo(room) }
            .verifyComplete()
        StepVerifier.create(resolve("handlerDomain", "4204")).verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `any domain resolves a registered id and refuses an unknown one`() {
        val message = keys.register(4205L, ChatDomain.MESSAGE)

        StepVerifier.create(resolve("anyDomain", "4205"))
            .assertNext { assertThat((it as VerifiedKey<*>).key).isEqualTo(message) }
            .verifyComplete()
        StepVerifier.create(resolve("anyDomain", "424243")).verifyError(KeyVerificationException::class.java)
    }
}
