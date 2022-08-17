package com.demo.chat.tracing.sleuth

import brave.sampler.Sampler
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean


open class TracingConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun sleuthTraceSampler(): Sampler = Sampler.NEVER_SAMPLE
}