package com.demo.chat.init.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
class DefaultIdentityConfiguration {

    /** Keep anon/admin keys here because later will be db bound **/
    @Value("\${app.service.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.service.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    fun <T> anonymousKey(typeUtil: TypeUtil<T>) = Supplier { AnonymousKey(typeUtil.fromString(anonymousId)) }

    @Bean
    fun <T> adminKey(typeUtil: TypeUtil<T>) = Supplier { AdminKey(typeUtil.fromString(adminId)) }
}