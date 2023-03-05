package com.demo.chat.rsocket

import com.demo.chat.domain.*
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.plugins.RSocketInterceptor
import org.reactivestreams.Publisher
import org.springframework.core.Ordered
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.messaging.rsocket.RSocketStrategies
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/*
   This strategy attempts to decode payload, then set a 'targetId' property on the context
   for later use by the PayloadInterceptor
 */
class TargetIdentifierInterceptor<T>(private val strategies: RSocketStrategies, typeUtil: TypeUtil<T>) :
    RSocketInterceptor, Ordered {

    private val targetTypeClass = Class.forName(typeUtil.parameterizedType().type.typeName)

    override fun apply(rSocket: RSocket): RSocket {
        val r = object : RSocket {
            override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
                return rSocket.requestChannel(payloads)
            }

            override fun requestStream(payload: Payload): Flux<Payload> {
                val ctxAdd = getContextMap(payload)
                return rSocket
                    .requestStream(payload)
                    .contextWrite { ctx ->
                        ctx.putAllMap(ctxAdd)
                    }
                    .contextCapture()
            }

            override fun requestResponse(payload: Payload): Mono<Payload> {
                val ctxAdd = getContextMap(payload)
                return rSocket
                    .requestResponse(payload)
                    .contextWrite { ctx ->
                        ctx.putAllMap(ctxAdd)
                    }
                    .contextCapture()
            }

        }

        return r
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    private fun getContextMap(payload: Payload): Map<*, *> {
        val ctxAdd = mutableMapOf<String, String>()

        try {
            if (payload.data.hasRemaining()) {
                val resolution = ResolvableType.forClassWithGenerics(RequestResponse::class.java, targetTypeClass)
                val data = DefaultDataBufferFactory().wrap(payload.data)
                val decoder = strategies.decoder<RequestResponse<*>>(resolution, MediaType.APPLICATION_CBOR)
                val decoded: RequestResponse<*> = decoder.decode(
                    data,
                    resolution,
                    MediaType.APPLICATION_CBOR,   // This should be variable
                    emptyMap()
                ) as RequestResponse

                val target = when (decoded) {
                    is ByIdRequest<*> -> {
                        decoded.id
                    }

                    is MembershipRequest<*> -> {
                        decoded.roomId
                    }

                    is MessageSendRequest<*, *> -> {
                        decoded.dest
                    }

                    is MemberTopicRequest<*> -> {
                        decoded.topic
                    }

                    else -> ""
                }

                ctxAdd["target"] = target.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ctxAdd
    }
}