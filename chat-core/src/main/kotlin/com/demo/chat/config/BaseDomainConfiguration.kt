package com.demo.chat.config

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
open class BaseDomainConfiguration {

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    open fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean("TypeUtil")
    @ConditionalOnProperty("app.key.type", havingValue = "long")
    open fun longTypeUtil(): TypeUtil<Long> = LongUtil()

    @Value("\${app.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    @ConditionalOnMissingBean
    open fun <T> rootKeys(typeUtil: TypeUtil<T>): RootKeys<T> = RootKeys()

    @Bean
    open fun <T> anonymousKey(typeUtil: TypeUtil<T>): Supplier<Anon<T>> =
        Supplier { Anon(typeUtil.fromString(anonymousId)) }

    @Bean
    open fun <T> adminKey(typeUtil: TypeUtil<T>): Supplier<Admin<T>> =
        Supplier { Admin(typeUtil.fromString(adminId)) }
}