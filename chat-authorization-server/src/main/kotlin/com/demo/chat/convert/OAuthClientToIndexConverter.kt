package com.demo.chat.convert

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.stereotype.Component

@Component
class OAuthClientToIndexConverter : Converter<RegisteredClient, List<Pair<String, Any>>> {
    override fun convert(client: RegisteredClient): List<Pair<String, Any>> =
        listOf(
            Pair("client_id", client.clientId),
            Pair("id", client.id),
            Pair("name", client.clientName)
        )
}