package com.demo.chat.test

import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.key.MockKeyGeneratorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class, MockKeyGeneratorResolver::class)
class SpringSecurityAccessBrokerServiceLongKeyTests(k: IKeyGenerator<Long>) :
    SpringSecurityAccessBrokerServiceTests<Long>(k)

@Disabled
open class SpringSecurityAccessBrokerServiceTests<T>(private val keyGen: IKeyGenerator<T>) {

    val user = User.create(keyGen.nextKey(), "TEST USER", "SOMENAME", "http://test/image.png")
    private val targetKey = keyGen.nextKey()

    private fun roles(): List<String> = emptyList()
    private fun grantedAuthorities() = roles().map { SimpleGrantedAuthority("ROLE_$it") }

    private val anonId = keyGen.nextKey()
    private val domainKey = keyGen.nextKey()

    @Test
    fun `calls hasAccessToDomain with class kind returns allow`() {
        val broker: AuthMetadataAccessBroker<T> = BDDMockito.mock()
        val rootKeys: RootKeys<T> = BDDMockito.mock()

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        BDDMockito.given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val p = accessService.hasAccessToDomainByKind(User::class.java, "TEST")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isTrue
            }
            .verifyComplete()
    }

    @Test
    fun `calls hasAccessTo with primitives returns allow`() {
        val broker: AuthMetadataAccessBroker<T> = BDDMockito.mock()
        val rootKeys: RootKeys<T> = BDDMockito.mock()

        val returnVal = Mono.just(true)

        BDDMockito
            .given(broker.hasAccessByKeyId(anyObject(), anyObject(), anyObject()))
            .willReturn(returnVal)

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)
        val p = accessService.hasAccessTo(user.key.id, domainKey.id, "TEST")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isTrue
            }
            .verifyComplete()
    }

    @Test
    fun `calls hasAccessToDomain returns allow`() {
        val broker: AuthMetadataAccessBroker<T> = BDDMockito.mock()
        val rootKeys: RootKeys<T> = BDDMockito.mock()

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        BDDMockito.given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val p = accessService.hasAccessToDomain("User", "TEST")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isTrue
            }
            .verifyComplete()
    }

    @Test
    fun `calls hasAccessTo returns disallow`() {
        val broker: AuthMetadataAccessBroker<T> = BDDMockito.mock()
        val rootKeys: RootKeys<T> = BDDMockito.mock()

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        BDDMockito.given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        val p = accessService.hasAccessTo(targetKey, "TEST1")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isFalse()
            }
            .verifyComplete()
    }

    @Test
    fun `calls hasAccessTo returns allow`() {
        val broker: AuthMetadataAccessBroker<T> = BDDMockito.mock()
        val rootKeys: RootKeys<T> = BDDMockito.mock()


        BDDMockito.given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val accessService = SpringSecurityAccessBrokerService(broker, rootKeys)

        val p = accessService.hasAccessTo(targetKey, "TEST")

        StepVerifier
            .create(p)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isTrue()
            }
            .verifyComplete()
    }
}