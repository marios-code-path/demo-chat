package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomOwnerGrant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The room owner writer as a bean.
 *
 * **The condition is `auth`, and not `composite` alone.** The writer needs
 * `authorizationService`, and `AuthBeansConfiguration` registers that bean
 * under the same condition. So the writer exists exactly where the service it
 * needs exists.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
open class RoomOwnerGrantConfiguration<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    open fun roomOwnerGrant(): RoomOwnerGrant<T> =
        ContextRoomOwnerGrant(ContextIdentity(rootKeys), authorizationService, rootKeys, typeUtil)
}
