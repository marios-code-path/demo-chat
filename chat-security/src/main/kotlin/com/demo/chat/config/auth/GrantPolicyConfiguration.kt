package com.demo.chat.config.auth

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.service.PolicyGrantWriter
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.policy.ShippedGrantPolicies
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The grant policy writer as a bean. See `CHAT-qojwcatx`.
 *
 * **This bean is where a policy is enabled.** It enables the three shipped
 * policies alone. A new policy has no effect until it is added to this list.
 *
 * The condition and the package follow `RoomOwnerGrantConfiguration`. The
 * writer needs `authorizationService`, which exists under the same condition.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
open class GrantPolicyConfiguration<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    open fun policyGrantWriter(): PolicyGrantWriter<T> =
        PolicyGrantWriter(authorizationService, rootKeys, typeUtil, ShippedGrantPolicies.ALL)
}
