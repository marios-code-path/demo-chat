package com.demo.chat.test.knownkey

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.ChatIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The closed lists of domains and identities. See `CHAT-avduuqwp`. */
class ChatDomainTests {

    @Test
    fun `parse reads each wire name and nothing else`() {
        ChatDomain.entries.forEach { assertThat(ChatDomain.parse(it.wireName)).isEqualTo(it) }
        assertThat(ChatDomain.parse("KeyCredential")).isNull()
        assertThat(ChatDomain.parse("Key")).isNull()
        assertThat(ChatDomain.parse("user")).isNull()
        assertThat(ChatDomain.parse("Admin")).isNull()
    }

    @Test
    fun `there are eight domains, with the two end-to-end encryption domains`() {
        assertThat(ChatDomain.entries.map { it.wireName }).containsExactly(
            "User", "Message", "MessageTopic", "TopicMembership", "AuthMetadata", "KeyValuePair",
            "ConversationEpoch", "FrankingTag"
        )
    }

    @Test
    fun `an identity is not a domain`() {
        ChatIdentity.entries.forEach {
            assertThat(ChatIdentity.parse(it.wireName)).isEqualTo(it)
            assertThat(ChatDomain.parse(it.wireName)).isNull()
        }
        assertThat(ChatIdentity.entries.map { it.wireName }).containsExactly("Admin", "Anon")
    }
}
