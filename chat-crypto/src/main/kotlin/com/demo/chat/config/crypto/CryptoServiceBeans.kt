package com.demo.chat.config.crypto

import com.demo.chat.crypto.memory.*
import com.demo.chat.service.core.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the in-memory E2EE service implementations when app.service.crypto.backend=memory.
 * These are the default/dummy backends — real backends would use Redis or Cassandra
 * for the pre-key pool, device inbox, and franking storage.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.crypto", name = ["backend"], havingValue = "memory", matchIfMissing = false)
class CryptoServiceBeans<T> {

    @Bean
    fun deviceService(): DeviceService<T> =
        InMemoryDeviceService()

    @Bean
    fun preKeyService(): PreKeyService<T> =
        InMemoryPreKeyService()

    @Bean
    fun encryptedMessageService(): EncryptedMessageService<T> =
        InMemoryEncryptedMessageService()

    @Bean
    fun conversationSeqService(): ConversationSeqService<T> =
        InMemoryConversationSeqService()

    @Bean
    fun conversationEpochService(): ConversationEpochService<T> =
        InMemoryConversationEpochService()

    @Bean
    fun frankingService(): FrankingService<T> =
        InMemoryFrankingService()
}
