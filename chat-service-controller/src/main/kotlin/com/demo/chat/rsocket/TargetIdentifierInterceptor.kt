package com.demo.chat.rsocket

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

class TargetIdentifierInterceptor(private val strategies: RSocketStrategies) : RSocketInterceptor, Ordered {
    override fun apply(t: RSocket?): RSocket {
        val r = object : RSocket {
            override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
                return t!!.requestChannel(payloads)
            }

            override fun requestStream(payload: Payload): Flux<Payload> {
                println("RS INTERCEPTOR")
                val ctxAdd = contextUpdate(payload)
                return t!!
                    .requestStream(payload)
                    .contextWrite { ctx ->
                        ctx.putAllMap(ctxAdd)
                    }
                    .contextCapture()
            }

            override fun requestResponse(payload: Payload): Mono<Payload> {
                println("RR INTERCEPTOR")

                val ctxAdd = contextUpdate(payload)
                return t!!
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

    private fun contextUpdate(payload: Payload): Map<*, *> {
        val ctxAdd = mutableMapOf<String, String>()
        try {
            if (payload.data.hasRemaining()) {
                val data = DefaultDataBufferFactory().wrap(payload.data)
                val decoder =
                    strategies.decoder<Object>(ResolvableType.forClass(Map::class.java), MediaType.APPLICATION_CBOR)
                val decoded: Map<String, Any> = decoder.decode(
                    data,
                    ResolvableType.forClass(Map::class.java),
                    MediaType.APPLICATION_CBOR,
                    emptyMap()
                ) as Map<String, Any>

                decoded.keys.forEach() { dataType ->
                    when (dataType) {
                        "ByIdRequest" -> {
                            val thisData = decoded[dataType] as Map<*, *>
                            val id = thisData["id"] as String
                            ctxAdd["target"] = id
                        }

                        "MembershipRequest" -> {
                            val thisData = decoded[dataType] as Map<*, *>
                            val roomId = thisData["roomId"] as String
                            ctxAdd["target"] = roomId
                        }

                        "MessageSendRequest" -> {
                            val thisData = decoded[dataType] as Map<*, *>
                            val dest = thisData["dest"] as String
                            ctxAdd["target"] = dest
                        }

                        "MemberTopicRequest" -> {
                            val thisData = decoded[dataType] as Map<*, *>
                            val topic = thisData["member"] as String
                            ctxAdd["target"] = topic
                        }

                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (ctxAdd.containsKey("target")) {
            val target = ctxAdd["target"]
            println("Target: $target")
        }

        return ctxAdd
    }
}