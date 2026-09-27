package com.demo.chat.test.access

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.security.AccessBroker
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * The access service under the name that the expressions use.
 *
 * It sits outside `com.demo.chat.test.rsocket` on purpose. That package has a
 * bare `@ComponentScan`, so every other RSocket test context would scan it.
 */
@TestConfiguration
class ChatAccessTestConfiguration {
    @Bean
    fun chatAccess(access: AccessBroker<Long>, rootKeys: RootKeys<Long>, verifier: KeyVerifier<Long>) =
        SpringSecurityAccessBrokerService(access, rootKeys, verifier)
}
