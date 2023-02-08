package com.demo.chat.config

import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
open class TypeUtilConfiguration {

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    open fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    open fun longTypeUtil(): TypeUtil<Long> = LongUtil()

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    open fun anonymousUUIDKeySupplier(): Supplier<Key<UUID>> = Supplier { Key.funKey(UUID(0L, 0L)) }

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    open fun anonymousLongKeySupplier(): Supplier<Key<Long>> = Supplier { Key.funKey(0L) }

}