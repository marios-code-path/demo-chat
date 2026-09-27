package com.demo.chat.test.crypto

import com.demo.chat.crypto.memory.InMemoryConversationEpochService
import com.demo.chat.crypto.memory.InMemoryEncryptedMessageService
import com.demo.chat.crypto.memory.InMemoryFrankingService
import com.demo.chat.domain.EncryptedEnvelope
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.MessageKind
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

/**
 * The end-to-end encryption keys are minted through the key service, in
 * their own domains. See `CHAT-avduuqwp`, D1.
 */
class CryptoKeyDomainTests {

    private val rootKeys = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(rootKeys)
    private val verifier = KeyVerifier(keys, rootKeys)

    private val epochs = InMemoryConversationEpochService(keys)
    private val franking = InMemoryFrankingService(keys)
    private val messages = InMemoryEncryptedMessageService(franking)

    private val conversation = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
    private val device = keys.key(ChatDomain.USER).block()!!

    @Test
    fun `an epoch key resolves in CONVERSATION_EPOCH`() {
        val epoch = epochs.startEpoch(conversation).block()!!
        assertThat(verifier.resolve(epoch.key.id, ChatDomain.CONVERSATION_EPOCH).block()!!.key).isEqualTo(epoch.key)
    }

    @Test
    fun `a franking key resolves in FRANKING_TAG`() {
        val tag = franking.generateTag(conversation, 1L, device, MessageKind.PAIRWISE, "payload".toByteArray()).block()!!
        assertThat(verifier.resolve(tag.key.id, ChatDomain.FRANKING_TAG).block()!!.key).isEqualTo(tag.key)
    }

    @Test
    fun `send returns a tag registered under FRANKING_TAG`() {
        val envelope = EncryptedEnvelope.create(
            keys.key(ChatDomain.MESSAGE).block()!!, conversation, device, device, device,
            1L, MessageKind.PAIRWISE, "ciphertext".toByteArray(),
        )
        val tag = messages.send(envelope).block()!!
        assertThat(keys.rootOf(tag.key.id).block()).isEqualTo(rootKeys.of(ChatDomain.FRANKING_TAG).id)
    }

    @Test
    fun `an epoch key and a franking key refuse each other's domain`() {
        val epoch = epochs.startEpoch(conversation).block()!!
        val tag = franking.generateTag(conversation, 1L, device, MessageKind.PAIRWISE, "payload".toByteArray()).block()!!

        StepVerifier.create(verifier.resolve(epoch.key.id, ChatDomain.FRANKING_TAG))
            .verifyError(KeyVerificationException::class.java)
        StepVerifier.create(verifier.resolve(tag.key.id, ChatDomain.CONVERSATION_EPOCH))
            .verifyError(KeyVerificationException::class.java)
    }
}
