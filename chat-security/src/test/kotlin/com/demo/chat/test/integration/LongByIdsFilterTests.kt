package com.demo.chat.test.integration

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.security.access.core.PersistenceAccess
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.TestBase.TestBase.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.config.TestLongUserDetailsConfiguration
import com.demo.chat.test.config.WithLongCustomChatUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.stereotype.Service
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux

/**
 * **`byIds` answers the permitted entities alone.** `CHAT-wkwiipgy` chose the
 * filter contract for a many target read.
 *
 * - Each target is evaluated on its own.
 * - A denied target is left out, and the request does not fail.
 * - An empty list, or a list with no permitted target, answers nothing.
 * - Self authority permits the caller's own target and no other.
 *
 * This runs through the method security proxy. The store answers an entity for
 * every key it receives, so only the filter can remove one.
 */
@SpringBootTest(
    classes = [
        TestLongKeyServiceBeans::class,
        TestLongPersistenceBeans::class,
        TestLongUserDetailsConfiguration::class,
        TestLongCompositeServiceBeans::class,
        TestKeyService::class,
        MethodSecurityIntegrationTestConfiguration::class
    ]
)
@ExtendWith(SpringExtension::class)
@WithLongCustomChatUser(userId = CALLER_ID, roles = [])
class LongByIdsFilterTests {

    @MockitoBean
    private lateinit var authService: AuthorizationService<Long, AuthMetadata<Long>>

    @Autowired
    private lateinit var beans: PersistenceServiceBeans<Long, String>

    @Autowired
    private lateinit var userPersistence: PersistenceStore<Long, User<Long>>

    @Autowired
    private lateinit var membershipPersistence: PersistenceStore<Long, TopicMembership<Long>>

    @BeforeEach
    fun grantOneTargetAndEchoEveryKey() {
        BDDMockito.given(authService.getAuthorizationsAgainst(anyObject(), anyObject(), anyObject()))
            .willAnswer { call ->
                val target = call.getArgument<Key<Long>>(1)
                if (target == PERMITTED) Flux.just(
                    AuthMetadata.create(Key.funKey(99L), CALLER, PERMITTED, "GET", false, Long.MAX_VALUE)
                )
                else Flux.empty<AuthMetadata<Long>>()
            }

        // The store is a context bean and is not reset between tests. This form
        // does not call the stubbed method, so the earlier answer never runs.
        BDDMockito.willAnswer { call ->
            Flux.fromIterable(call.getArgument<List<Key<Long>>>(0).map { user(it) })
        }.given(beans.userPersistence()).byIds(anyObject())

        BDDMockito.willAnswer { call ->
            Flux.fromIterable(call.getArgument<List<Key<Long>>>(0).map { TopicMembership.create(it.id, 5L, 6L) })
        }.given(beans.membershipPersistence()).byIds(anyObject())
    }

    @Test
    fun `a mixed list answers the permitted entity alone`() {
        assertThat(keysOf(listOf(PERMITTED, DENIED))).containsExactly(PERMITTED)
    }

    @Test
    fun `the caller and a denied target answer the caller alone`() {
        assertThat(keysOf(listOf(CALLER, DENIED))).containsExactly(CALLER)
    }

    @Test
    fun `the caller, a permitted and a denied target answer the first two`() {
        assertThat(keysOf(listOf(CALLER, PERMITTED, DENIED))).containsExactly(CALLER, PERMITTED)
    }

    @Test
    fun `a fully denied list answers nothing and does not fail`() {
        assertThat(keysOf(listOf(DENIED))).isEmpty()
    }

    @Test
    fun `an empty list answers nothing`() {
        assertThat(keysOf(listOf())).isEmpty()
    }

    /**
     * **A membership carries its key as a raw id**, not as a `Key`. The filter
     * must still name the right target. `filterObject.key` alone failed here
     * with `EL1004E` for a `Long` id. See `EntityTargets`.
     */
    @Test
    fun `a mixed membership list answers the permitted membership alone`() {
        val answer = membershipPersistence.byIds(listOf(PERMITTED, DENIED)).map { it.key }.collectList().block()!!

        assertThat(answer).containsExactly(PERMITTED.id)
    }

    private fun keysOf(keys: List<Key<Long>>): List<Key<Long>> =
        userPersistence.byIds(keys).map { it.key }.collectList().block()!!

    private fun user(key: Key<Long>): User<Long> = User.create(key, "name", "handle", "http://u")
}

private const val CALLER_ID = 1L
private val CALLER: Key<Long> = Key.funKey(CALLER_ID)
private val PERMITTED: Key<Long> = Key.funKey(20L)
private val DENIED: Key<Long> = Key.funKey(30L)

/** A membership store behind the method security proxy. No production class implements this yet. */
@Service
class TestMembershipPersistence(that: PersistenceServiceBeans<Long, String>) :
    PersistenceAccess<Long, TopicMembership<Long>>,
    PersistenceStore<Long, TopicMembership<Long>> by that.membershipPersistence()
