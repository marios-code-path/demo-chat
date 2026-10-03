package com.demo.chat.client.rsocket

import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.Encoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux

class RSocketAuthenticationEncoder : Encoder<Any> {

    private val simple = SimpleAuthenticationEncoder()
    private val bearer = BearerTokenAuthenticationEncoder()

    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        if (mimeType != null && !MIME_TYPE.isCompatibleWith(mimeType)) {
            return false
        }
        val type = elementType.toClass()
        return UsernamePasswordMetadata::class.java.isAssignableFrom(type) ||
            BearerTokenMetadata::class.java.isAssignableFrom(type)
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?,
    ): Flux<DataBuffer> = Flux.from(inputStream)
        .map { value -> encodeValue(value, bufferFactory, elementType, mimeType, hints) }

    override fun encodeValue(
        value: Any,
        bufferFactory: DataBufferFactory,
        valueType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?,
    ): DataBuffer = when (value) {
        is UsernamePasswordMetadata -> simple.encodeValue(value, bufferFactory, valueType, mimeType, hints)
        is BearerTokenMetadata -> bearer.encodeValue(value, bufferFactory, valueType, mimeType, hints)
        else -> error("Unsupported RSocket authentication metadata: ${value::class.qualifiedName}")
    }

    override fun getEncodableMimeTypes(): MutableList<MimeType> = mutableListOf(MIME_TYPE)

    private companion object {
        val MIME_TYPE: MimeType = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
    }
}
