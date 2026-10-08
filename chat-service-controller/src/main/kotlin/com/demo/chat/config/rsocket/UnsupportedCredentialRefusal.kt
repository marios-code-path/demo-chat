package com.demo.chat.config.rsocket

import io.netty.buffer.Unpooled
import io.rsocket.metadata.AuthMetadataCodec
import io.rsocket.metadata.WellKnownAuthType
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.core.Ordered
import org.springframework.core.codec.ByteArrayDecoder
import org.springframework.messaging.rsocket.DefaultMetadataExtractor
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.rsocket.PayloadInterceptorOrder
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.api.PayloadInterceptor
import org.springframework.security.rsocket.api.PayloadInterceptorChain
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Mono

/**
 * Refuses a credential that the seam cannot read. See `CHAT-jkordfef`.
 *
 * `AuthenticationPayloadExchangeConverter` answers no authentication for an
 * auth type that is not well known. The anonymous filter then installs the
 * `Anon` identity, so a caller that sent a credential took `Anon` in silence.
 * The legacy `basic` MIME type had the same result, because no converter here
 * reads it.
 *
 * **A caller that sends a credential never takes `Anon`.** Only a caller that
 * sends no authentication entry takes it. The owner chose this on 2026-10-07.
 *
 * The refusal is a `BadCredentialsException`, so it leaves as `0x401` on a
 * request and as `RejectedSetupException` on a setup frame. It runs one step
 * before authentication.
 */
class UnsupportedCredentialRefusal : PayloadInterceptor, Ordered {

    private val extractor = DefaultMetadataExtractor(ByteArrayDecoder()).apply {
        metadataToExtract(AUTHENTICATION, ByteArray::class.java, AUTHENTICATION_KEY)
        metadataToExtract(LEGACY_BASIC, ByteArray::class.java, LEGACY_BASIC_KEY)
    }

    override fun getOrder(): Int = PayloadInterceptorOrder.AUTHENTICATION.order - 1

    override fun intercept(exchange: PayloadExchange, chain: PayloadInterceptorChain): Mono<Void> =
        Mono.fromCallable { extractor.extract(exchange.payload, COMPOSITE) }
            .flatMap { metadata ->
                val authentication = metadata[AUTHENTICATION_KEY] as ByteArray?
                when {
                    metadata.containsKey(LEGACY_BASIC_KEY) -> refuse("the legacy basic authentication MIME type")
                    authentication != null && !isSupported(authentication) -> refuse("an unsupported auth type")
                    else -> chain.next(exchange)
                }
            }

    private fun refuse(what: String): Mono<Void> =
        Mono.error(BadCredentialsException("The authentication metadata uses $what."))

    private fun isSupported(entry: ByteArray): Boolean {
        if (entry.isEmpty()) return false
        val buffer = Unpooled.wrappedBuffer(entry)
        return try {
            AuthMetadataCodec.isWellKnownAuthType(buffer) &&
                AuthMetadataCodec.readWellKnownAuthType(buffer) in SUPPORTED
        } finally {
            buffer.release()
        }
    }

    private companion object {
        val COMPOSITE = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string)
        val AUTHENTICATION = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        val LEGACY_BASIC = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.basic.v0")
        val SUPPORTED = setOf(WellKnownAuthType.SIMPLE, WellKnownAuthType.BEARER)
        const val AUTHENTICATION_KEY = "authentication"
        const val LEGACY_BASIC_KEY = "legacyBasicAuthentication"
    }
}
