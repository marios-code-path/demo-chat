package com.demo.chat.config

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.AdminKey
import com.demo.chat.domain.knownkey.AnonymousKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
open class TypeUtilConfiguration {
    // TODO: change app.service.core.key to app.key.type
    @Bean("TypeUtil")
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    open fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    open fun longTypeUtil(): TypeUtil<Long> = LongUtil()

    @Value("\${app.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    open fun <T> anonymousKey(typeUtil: TypeUtil<T>): Supplier<AnonymousKey<T>> =
        Supplier { AnonymousKey(typeUtil.fromString(anonymousId)) }

    @Bean
    open fun <T> adminKey(typeUtil: TypeUtil<T>): Supplier<AdminKey<T>> =
        Supplier { AdminKey(typeUtil.fromString(adminId)) }

}