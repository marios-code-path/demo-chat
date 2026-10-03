package com.demo.chat.config.rsocket

import com.demo.chat.security.rsocket.RSocketSecurityErrorKind
import com.demo.chat.security.rsocket.RSocketSecurityErrorPayload
import io.rsocket.exceptions.ApplicationErrorException
import org.springframework.core.Ordered
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.api.PayloadInterceptor
import org.springframework.security.rsocket.api.PayloadInterceptorChain
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

class RSocketSecurityErrorPayloadInterceptor : PayloadInterceptor, Ordered {

    private val mapper = JsonMapper.builder().build()

    override fun getOrder(): Int = 100

    override fun intercept(exchange: PayloadExchange, chain: PayloadInterceptorChain): Mono<Void> =
        chain.next(exchange).onErrorMap(AuthenticationException::class.java) { error ->
            ApplicationErrorException(encode(RSocketSecurityErrorKind.AUTHENTICATION), error)
        }.onErrorMap(AccessDeniedException::class.java) { error ->
            ApplicationErrorException(encode(RSocketSecurityErrorKind.AUTHORIZATION), error)
        }

    private fun encode(kind: RSocketSecurityErrorKind): String = mapper.writeValueAsString(
        RSocketSecurityErrorPayload(VERSION, kind)
    )

    private companion object {
        const val VERSION = 1
    }
}
