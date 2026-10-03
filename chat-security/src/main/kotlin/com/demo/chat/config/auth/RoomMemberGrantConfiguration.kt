package com.demo.chat.config.auth

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.service.MembershipSendGrant
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomMemberGrant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The member `SEND` writer as a bean.
 *
 * The condition and the package follow `RoomOwnerGrantConfiguration`. The
 * writer needs `authorizationService`, which exists under the same condition.
 * `ChatApp` scans `com.demo.chat.config` alone, so this class must sit under
 * that root.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
open class RoomMemberGrantConfiguration<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    open fun roomMemberGrant(): RoomMemberGrant<T> =
        MembershipSendGrant(authorizationService, rootKeys, typeUtil)
}
