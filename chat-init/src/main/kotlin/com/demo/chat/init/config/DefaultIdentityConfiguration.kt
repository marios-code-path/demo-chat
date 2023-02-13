package com.demo.chat.init.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.AdminKey
import com.demo.chat.domain.knownkey.AnonymousKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
class DefaultIdentityConfiguration {

    // TODO change app.service.identity.* to app.identity.*
    /** Keep anon/admin keys here because later will be db bound **/

}