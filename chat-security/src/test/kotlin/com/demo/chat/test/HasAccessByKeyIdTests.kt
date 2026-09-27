package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import reactor.core.publisher.Flux

/**
 * A raw id check resolves both ids before the broker reads a grant. The
 * principal resolves in USER. A failed resolution denies. See `CHAT-avduuqwp`,
 * C4.
 */
@Suppress("UNCHECKED_CAST")
class HasAccessByKeyIdTests {

    private val roots = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(roots)
    private val grants = (mock(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>).also {
        given(it.getAuthorizationsAgainst(TestBase.anyObject(), TestBase.anyObject(), TestBase.anyObject()))
            .willReturn(Flux.empty())
    }
    private val broker = AuthMetadataAccessBroker(grants, KeyVerifier(keys, roots))

    @Test
    fun `two resolved ids reach the broker, and a key holds every right over itself`() {
        val user = keys.key(ChatDomain.USER).block()!!
        assertThat(broker.hasAccessByKeyId(user.id, user.id, "GET").block()).isTrue()
    }

    @Test
    fun `an unknown target id denies`() {
        val user = keys.key(ChatDomain.USER).block()!!
        assertThat(broker.hasAccessByKeyId(user.id, 424242L, "GET").block()).isFalse()
    }

    @Test
    fun `a principal outside USER denies`() {
        val message = keys.key(ChatDomain.MESSAGE).block()!!
        assertThat(broker.hasAccessByKeyId(message.id, message.id, "GET").block()).isFalse()
    }
}
