package com.demo.chat.client.rsocket

import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.util.MimeType

data class RequestMetadata(val value: Any, val mimeType: MimeType)

class MetadataRSocketRequester(
    private val r: RSocketRequester,
    private val metadataProvider: () -> Any
) : RSocketRequester by r {
    override fun route(route: String, vararg routeVars: Any): RSocketRequester.RequestSpec {
        return when(val metadata = metadataProvider()) {
            is RequestMetadata ->  r.route(route, *routeVars).metadata(metadata.value, metadata.mimeType)
            else -> r.route(route, *routeVars)
        }
    }
}