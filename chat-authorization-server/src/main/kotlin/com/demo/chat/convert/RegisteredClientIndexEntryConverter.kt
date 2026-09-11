package com.demo.chat.convert

import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient

/**
 * Registers the index fields of a [RegisteredClient].
 *
 * [KeyValueStoreRegisteredClientRepository] queries the key-value index on
 * `id` and on `client_id`, so both fields must be indexed.
 *
 * This replaces a converter that implemented `com.demo.chat.convert.Converter`.
 * That interface no longer extends the Spring converter interface, so a
 * Spring ConversionService never held the converter and the key-value index
 * failed on its first write.
 */
@Configuration
@Profile("client-kv-store")
open class RegisteredClientIndexEntryConverter {

    @Bean
    open fun registeredClientIndexFields(): KeyValueIndexFieldsEntry =
        KeyValueIndexFieldsEntry(
            RegisteredClient::class.java,
            KeyValueIndexFields { value ->
                val client = value as RegisteredClient
                listOf(
                    Pair("client_id", client.clientId),
                    Pair("id", client.id),
                    Pair("name", client.clientName),
                )
            }
        )
}
