package com.demo.chat

import io.rsocket.AbstractRSocket
import io.rsocket.Payload
import io.rsocket.util.DefaultPayload
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TestRSocket(private val data: String, private val metadata: String) : AbstractRSocket() {

    override fun requestResponse(payload: Payload): Mono<Payload> {
        return Mono.just(DefaultPayload.create(data, metadata))
    }

    override fun requestStream(payload: Payload): Flux<Payload> {
        return Flux.range(1, 10000).flatMap { requestResponse(payload) }
    }

    override fun metadataPush(payload: Payload): Mono<Void> {
        return Mono.empty()
    }

    override fun fireAndForget(payload: Payload): Mono<Void> {
        return Mono.empty()
    }

    override fun requestChannel(payloads: Publisher<Payload>?): Flux<Payload> {
        // TODO is defensive copy neccesary?
        return Flux.from(payloads!!).map(DefaultPayload::create)
        }
}
