package com.demo.chat.controller.webflux.resolve

import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Builds the `VerifiedKey` of a [Resolved] path id. See `CHAT-avduuqwp`, D2.
 *
 * The resolver reads the path segment, converts it to the key type through
 * `TypeUtil`, and resolves it through `KeyVerifier`. A malformed id, an
 * unknown id, and an id outside the domain each fail with
 * `KeyVerificationException`. The handler does not run.
 *
 * The beans are read at the first request, because a `WebFluxConfigurer`
 * runs before the key registry beans exist.
 */
class ResolvedKeyArgumentResolver(
    private val verifiers: ObjectProvider<KeyVerifier<*>>,
    private val typeUtils: ObjectProvider<TypeUtil<*>>,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(Resolved::class.java) &&
            VerifiedKey::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        bindingContext: BindingContext,
        exchange: ServerWebExchange,
    ): Mono<Any> = Mono.defer {
        val annotation = parameter.getParameterAnnotation(Resolved::class.java)!!
        val name = annotation.name.ifEmpty { parameter.parameterName ?: "" }
        val variables = exchange.getAttribute<Map<String, String>>(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
        val segment = variables?.get(name)
            ?: return@defer Mono.error(KeyVerificationException("The path holds no variable '$name'."))

        @Suppress("UNCHECKED_CAST")
        val verifier = verifiers.getObject() as KeyVerifier<Any?>
        // A missing bean is a configuration fault, so it stays outside the catch.
        val typeUtil = typeUtils.getObject()
        val id = try {
            typeUtil.fromString(segment)
        } catch (error: RuntimeException) {
            return@defer Mono.error(KeyVerificationException("The path id '$segment' is not a key id."))
        }

        verifier.resolve(id, domainOf(annotation, exchange)).map { it as Any }
    }

    private fun domainOf(annotation: Resolved, exchange: ServerWebExchange): ChatDomain? = when {
        annotation.anyDomain -> null
        annotation.domain.size == 1 -> annotation.domain.single()
        annotation.domain.isEmpty() -> {
            val handler = exchange.getAttribute<Any>(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
            // The stored handler method can hold the bean name rather than the bean.
            val bean = (handler as? HandlerMethod)?.bean
                ?.let { if (it is String) exchange.applicationContext?.getBean(it) else it }
            (bean as? DomainScoped)?.domain()
                ?: throw IllegalStateException(
                    "A @Resolved id with no domain needs a DomainScoped handler. The handler is ${bean?.javaClass?.name}."
                )
        }
        else -> throw IllegalStateException("A @Resolved id names one domain at most.")
    }
}
