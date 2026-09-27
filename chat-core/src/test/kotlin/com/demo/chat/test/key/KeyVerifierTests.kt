package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

/**
 * The verifier against a fake registry. `chat-core` cannot depend on a
 * persistence module, so the registry is `TestGeneratorKeyService`. See
 * `CHAT-avduuqwp`.
 */
class KeyVerifierTests {

    private val rootKeys = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(rootKeys)
    private val verifier = KeyVerifier(keys, rootKeys)

    @Test
    fun `a minted key verifies`() {
        val minted = keys.key(ChatDomain.USER).block()!!
        assertThat(verifier.verify(minted, ChatDomain.USER).block()!!.key).isEqualTo(minted)
        assertThat(verifier.verify(minted, null).block()!!.key).isEqualTo(minted)
    }

    @Test
    fun `a forged root is refused`() {
        val minted = keys.key(ChatDomain.USER).block()!!
        StepVerifier.create(verifier.verify(Key.of(minted.id, rootKeys.of(ChatDomain.MESSAGE).id), null))
            .verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `a key of another domain is refused`() {
        val minted = keys.key(ChatDomain.USER).block()!!
        StepVerifier.create(verifier.verify(minted, ChatDomain.MESSAGE_TOPIC))
            .verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `an unknown id is refused`() {
        StepVerifier.create(verifier.resolve(424242L, null)).verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `an epoch key with the franking root is refused, and the reverse`() {
        val epoch = keys.key(ChatDomain.CONVERSATION_EPOCH).block()!!
        val tag = keys.key(ChatDomain.FRANKING_TAG).block()!!

        StepVerifier.create(verifier.verify(Key.of(epoch.id, rootKeys.of(ChatDomain.FRANKING_TAG).id), null))
            .verifyError(KeyVerificationException::class.java)
        StepVerifier.create(verifier.verify(Key.of(tag.id, rootKeys.of(ChatDomain.CONVERSATION_EPOCH).id), null))
            .verifyError(KeyVerificationException::class.java)
    }

    @Test
    fun `resolve reads the stored root`() {
        val minted = keys.key(ChatDomain.USER).block()!!
        assertThat(verifier.resolve(minted.id, ChatDomain.USER).block()!!.key).isEqualTo(minted)
    }

    @Test
    fun `a root key resolves to itself`() {
        val root = rootKeys.of(ChatDomain.USER)
        assertThat(verifier.resolve(root.id, null).block()!!.key).isEqualTo(root)
    }
}
