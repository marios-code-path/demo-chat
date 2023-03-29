package com.demo.chat.config

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
open class BaseDomainConfiguration {

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    open fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.key.type", havingValue = "long")
    open fun longTypeUtil(): TypeUtil<Long> = LongUtil()

    @Bean
    @ConditionalOnMissingBean
    open fun <T> rootKeys(typeUtil: TypeUtil<T>): RootKeys<T> = RootKeys()
}