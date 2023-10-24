package com.demo.chat.client.rsocket

import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.util.MimeType
import java.util.function.Supplier

sealed interface RequestMetadata
data class SimpleRequestMetadata(val value: Any, val mimeType: MimeType) : RequestMetadata
object EmptyRequestMetadata : RequestMetadata

class MetadataRSocketRequester(
    private val r: RSocketRequester,
    private val metadataProvider: Supplier<*>
) : RSocketRequester by r {
    override fun route(route: String, vararg routeVars: Any): RSocketRequester.RequestSpec {
        return when(val metadata = metadataProvider.get()) {
            is SimpleRequestMetadata -> { r.route(route, *routeVars).metadata(metadata.value, metadata.mimeType) }
            else -> r.route(route, *routeVars)
        }
    }
}