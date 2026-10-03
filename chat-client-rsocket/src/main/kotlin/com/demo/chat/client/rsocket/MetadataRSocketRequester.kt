package com.demo.chat.client.rsocket

import com.demo.chat.config.agent.AgentAuthenticationToken
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Consumer
import java.util.function.Supplier

sealed interface RequestMetadata
data class SimpleRequestMetadata(val value: Any, val mimeType: MimeType) : RequestMetadata
object EmptyRequestMetadata : RequestMetadata

class MetadataRSocketRequester(
    private val r: RSocketRequester,
    private val metadataProvider: Supplier<*>,
) : RSocketRequester by r {

    override fun route(route: String, vararg routeVars: Any): RSocketRequester.RequestSpec =
        DeferredRequestSpec(r, route, routeVars, metadataProvider)

    private class DeferredRequestSpec(
        private val requester: RSocketRequester,
        private val route: String,
        private val routeVars: Array<out Any>,
        private val metadataProvider: Supplier<*>,
    ) : RSocketRequester.RequestSpec {

        private val metadataActions = mutableListOf<(RSocketRequester.RequestSpec) -> Unit>()
        private var data: DataCall? = null

        override fun metadata(value: Any, mimeType: MimeType): RSocketRequester.RequestSpec {
            metadataActions += { spec -> spec.metadata(value, mimeType) }
            return this
        }

        override fun metadata(consumer: Consumer<RSocketRequester.MetadataSpec<*>>): RSocketRequester.RequestSpec {
            metadataActions += { spec -> spec.metadata(consumer) }
            return this
        }

        override fun data(value: Any): RSocketRequester.RetrieveSpec {
            data = DataCall.Value(value)
            return this
        }

        override fun data(value: Any, elementClass: Class<*>): RSocketRequester.RetrieveSpec {
            data = DataCall.ClassValue(value, elementClass)
            return this
        }

        override fun data(value: Any, elementType: ParameterizedTypeReference<*>): RSocketRequester.RetrieveSpec {
            data = DataCall.TypeValue(value, elementType)
            return this
        }

        override fun sendMetadata(): Mono<Void> = prepared().flatMap { it.sendMetadata() }
            .onErrorMap(CoreSecurityErrorDecoder::decode)

        override fun send(): Mono<Void> = prepared().flatMap { it.send() }
            .onErrorMap(CoreSecurityErrorDecoder::decode)

        override fun <T : Any> retrieveMono(elementClass: Class<T>): Mono<T> =
            prepared().flatMap { it.retrieveMono(elementClass) }
                .onErrorMap(CoreSecurityErrorDecoder::decode)

        override fun <T : Any> retrieveMono(elementType: ParameterizedTypeReference<T>): Mono<T> =
            prepared().flatMap { it.retrieveMono(elementType) }
                .onErrorMap(CoreSecurityErrorDecoder::decode)

        override fun <T : Any> retrieveFlux(elementClass: Class<T>): Flux<T> =
            Flux.defer { prepared().flatMapMany { it.retrieveFlux(elementClass) } }
                .onErrorMap(CoreSecurityErrorDecoder::decode)

        override fun <T : Any> retrieveFlux(elementType: ParameterizedTypeReference<T>): Flux<T> =
            Flux.defer { prepared().flatMapMany { it.retrieveFlux(elementType) } }
                .onErrorMap(CoreSecurityErrorDecoder::decode)

        private fun prepared(): Mono<RSocketRequester.RequestSpec> =
            currentMetadata().map { metadata ->
                var spec = requester.route(route, *routeVars)
                if (metadata is SimpleRequestMetadata) {
                    spec = spec.metadata(metadata.value, metadata.mimeType)
                }
                metadataActions.forEach { action -> action(spec) }
                when (val requestData = data) {
                    null -> spec
                    is DataCall.Value -> spec.data(requestData.value) as RSocketRequester.RequestSpec
                    is DataCall.ClassValue -> spec.data(requestData.value, requestData.elementClass) as RSocketRequester.RequestSpec
                    is DataCall.TypeValue -> spec.data(requestData.value, requestData.elementType) as RSocketRequester.RequestSpec
                }
            }

        private fun currentMetadata(): Mono<RequestMetadata> =
            ReactiveSecurityContextHolder.getContext()
                .flatMap { context ->
                    val authentication = context.authentication
                    if (authentication is AgentAuthenticationToken) {
                        Mono.just<RequestMetadata>(
                            SimpleRequestMetadata(
                                BearerTokenMetadata(authentication.jwt.tokenValue),
                                AGENT_BEARER_MIME_TYPE,
                            )
                        )
                    } else {
                        Mono.empty()
                    }
                }
                .switchIfEmpty(Mono.fromSupplier { metadataProvider.get() as? RequestMetadata ?: EmptyRequestMetadata })

        private sealed interface DataCall {
            data class Value(val value: Any) : DataCall
            data class ClassValue(val value: Any, val elementClass: Class<*>) : DataCall
            data class TypeValue(val value: Any, val elementType: ParameterizedTypeReference<*>) : DataCall
        }

        private companion object {
            val AGENT_BEARER_MIME_TYPE: MimeType =
                org.springframework.util.MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
        }
    }
}
