package com.demo.chat.tracing.sleuth

import brave.Tracer
import brave.Tracing
import brave.context.slf4j.MDCCurrentTraceContext
import brave.propagation.B3Propagation
import brave.propagation.ExtraFieldPropagation
import brave.sampler.Sampler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import zipkin2.Span
import zipkin2.reporter.Reporter
import kotlin.random.Random

class TracingConfiguration {
    @Bean
    fun tracing(
        @Value("\${spring.zipkin.service.name?spring.application.name:spring-tracing}") serviceName: String?,
        spanReporter: Reporter<Span?>?
    ): Tracing? {
        return Tracing
            .newBuilder()
            .sampler(Sampler.ALWAYS_SAMPLE)
            .localServiceName(serviceName)
            .propagationFactory(
                ExtraFieldPropagation
                    .newFactory(B3Propagation.FACTORY, "client-id")
            )
            .currentTraceContext(MDCCurrentTraceContext.create())
            .spanReporter(spanReporter)
            .build()
    }

    fun createASpan(tracer: Tracer, span: brave.Span) {
        val ws : Tracer.SpanInScope = tracer.withSpanInScope(span)
        val newSpan = tracer.nextSpan().name("Continual")

        newSpan.tag("numbers", Random.nextLong().toString())

    }
}