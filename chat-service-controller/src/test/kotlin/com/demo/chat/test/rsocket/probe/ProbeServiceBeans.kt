package com.demo.chat.test.rsocket.probe

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.config.TestCompositeServiceBeans
import com.demo.chat.test.key.FakeKeyServices
import org.mockito.BDDMockito.given
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux

/**
 * The service beans and the key registry of the boundary probes.
 *
 * **The registry is real, and a mock cannot replace it.**
 * `chatAccess.hasAccessToDomain` reads the domain root through
 * `KeyVerifier.domainRoot`, which wraps the answer in a `VerifiedKey`. That
 * key is non-null. A mocked registry answers null, and the expression then
 * fails with `Parameter specified as non-null is null`. That failure reads
 * like a refusal and it is not one. `FakeKeyServices` builds a root set that
 * holds every domain.
 */
@TestConfiguration
class ProbeServiceBeans {

    @Bean
    fun rootKeys(): RootKeys<Long> = FakeKeyServices.longRoots()

    @Bean
    fun compositeServiceBeans(): TestCompositeServiceBeans<Long, String> =
        TestCompositeServiceBeans<Long, String>().also {
            given(it.mockTopicBean.listRooms()).willReturn(Flux.empty())
        }
}
