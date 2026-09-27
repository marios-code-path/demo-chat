package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.controller.core.PersistenceControllersConfiguration
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexJobStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.Instant

/**
 * Each write path of section E succeeds through its caller, over the real
 * memory stores, which refuse a key of another domain. See `CHAT-avduuqwp`,
 * T5 step 6.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-store-write-paths", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.service.core.vector=embedded", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class StoreWritePathTests {
    @Autowired
    lateinit var composite: CompositeServiceBeans<Long, String>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var keyBeans: KeyServiceBeans<Long>

    @Autowired
    lateinit var rootKeys: RootKeys<Long>

    @Autowired
    lateinit var jobStore: VectorIndexJobStore<Long>

    @Autowired
    lateinit var reindex: MessageReindexService<Long>

    @Autowired
    // The interface declares out M, so the field type needs no wildcard for Spring to match it.
    lateinit var grants: AuthorizationService<Long, @JvmSuppressWildcards AuthMetadata<Long>>

    @Autowired
    lateinit var userRoute: PersistenceControllersConfiguration.UserPersistenceController<Long, String>

    @Autowired
    lateinit var keyValueRoute: PersistenceControllersConfiguration.KeyValuePersistenceController<Long, String>

    private val timeout = Duration.ofSeconds(10)

    private fun root(domain: ChatDomain) = rootKeys.of(domain).id

    // The handle index tokenizes on a hyphen, so each handle is one token.
    private fun newUser(handle: String): Key<Long> =
        composite.userService().addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!

    // The topic name index tokenizes the same way.
    private fun newRoom(name: String): Key<Long> =
        composite.topicService().addRoom(ByStringRequest(name)).block(timeout)!!

    // E1
    @Test
    fun `a sent message is stored under a MESSAGE key`() {
        val user = newUser("e1sender")
        val room = newRoom("e1room")

        val sent = composite.messageService().send(MessageSendRequest("hello", user.id, room.id)).block(timeout)!!

        assertThat(sent.root).isEqualTo(root(ChatDomain.MESSAGE))
        assertThat(stores.messagePersistence().get(sent).block(timeout)!!.key.id).isEqualTo(sent.id)
    }

    // E2
    @Test
    fun `an added user is stored under a USER key`() {
        val user = newUser("e2user")

        assertThat(user.root).isEqualTo(root(ChatDomain.USER))
        assertThat(stores.userPersistence().get(user).block(timeout)!!.handle).isEqualTo("e2user")
    }

    // E3
    @Test
    fun `an added room is stored under a MESSAGE_TOPIC key`() {
        val room = newRoom("e3room")

        assertThat(room.root).isEqualTo(root(ChatDomain.MESSAGE_TOPIC))
        assertThat(stores.topicPersistence().get(room).block(timeout)!!.data).isEqualTo("e3room")
    }

    // E4
    @Test
    fun `a join stores a membership under a TOPIC_MEMBERSHIP key`() {
        val user = newUser("e4user")
        val room = newRoom("e4room")

        composite.topicService().joinRoom(MembershipRequest(user.id, room.id)).block(timeout)

        val membership = stores.membershipPersistence().all().collectList().block(timeout)!!
            .single { it.member == user.id && it.memberOf == room.id }
        assertThat(keyBeans.keyService().rootOf(membership.key).block(timeout)).isEqualTo(root(ChatDomain.TOPIC_MEMBERSHIP))
    }

    // E5 and E6
    @Test
    fun `a job stores its topic and its record under separate domains`() {
        val job = jobStore.createJob(Instant.now()).block(timeout)!!

        assertThat(job.key.root).isEqualTo(root(ChatDomain.KEY_VALUE_PAIR))
        assertThat(job.topicKey.root).isEqualTo(root(ChatDomain.MESSAGE_TOPIC))
        assertThat(stores.topicPersistence().get(job.topicKey).block(timeout)).isNotNull
        assertThat(jobStore.readJob(job.key).block(timeout)!!.topicKey).isEqualTo(job.topicKey)
    }

    // E7
    @Test
    fun `a rebuild stores its job records under MESSAGE keys`() {
        reindex.start().block(timeout)
        val deadline = Instant.now().plusSeconds(10)
        while (reindex.status().running && Instant.now().isBefore(deadline)) Thread.sleep(20)

        val job = jobStore.readJob(reindex.status().coveringJob!!).block(timeout)!!
        val records = stores.messagePersistence().all().collectList().block(timeout)!!
            .filter { it.key.dest == job.topicKey.id }
        assertThat(records).isNotEmpty
        assertThat(records.map { it.key.root }).containsOnly(root(ChatDomain.MESSAGE))
    }

    // E10
    @Test
    fun `the RSocket add route stores a user under a USER key`() {
        val key = stores.userPersistence().key().block(timeout)!!

        userRoute.addRoute(User.create(key, "name-e10", "e10-user", "http://u")).block(timeout)

        assertThat(stores.userPersistence().get(key).block(timeout)!!.handle).isEqualTo("e10-user")
    }

    // E12. app.users.create runs the initialization, which loads the identities
    // from the users it stores. Without it, no initial user exists.
    @Test
    fun `the initial users are stored under USER keys`() {
        listOf(rootKeys.admin(), rootKeys.anon()).forEach { identity ->
            assertThat(identity.root).isEqualTo(root(ChatDomain.USER))
            assertThat(stores.userPersistence().get(identity).block(timeout)).isNotNull
        }
    }

    // E14
    @Test
    fun `a grant is stored under an AUTH_METADATA key`() {
        val user = newUser("e14user")
        val room = newRoom("e14room")
        val placeholder = Key.empty(0L, root(ChatDomain.AUTH_METADATA))

        grants.authorize(AuthMetadata.create(placeholder, user, room, "GET", Long.MAX_VALUE), true).block(timeout)

        // The store read avoids the actor set, which needs the loaded identities.
        val grant = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .single { it.principal == user && it.target == room }
        assertThat(grant.key.root).isEqualTo(root(ChatDomain.AUTH_METADATA))
    }

    // E15 and E9 on the RSocket side
    @Test
    fun `a key value pair minted by the shell path is stored under a KEY_VALUE_PAIR key`() {
        val key = keyBeans.keyService().key(ChatDomain.KEY_VALUE_PAIR).block(timeout)!!

        keyValueRoute.addRoute(KeyValuePair.create(key, "value" as Any)).block(timeout)

        assertThat(stores.keyValuePersistence().get(key).block(timeout)!!.data).isEqualTo("value")
    }

    // The same route refuses a key of another domain. See T5 step 4.
    @Test
    fun `the key value route refuses a topic key`() {
        val room = newRoom("e15room")

        assertThat(runCatching { keyValueRoute.addRoute(KeyValuePair.create(room, "job" as Any)).block(timeout) }.exceptionOrNull())
            .isNotNull
        assertThat(stores.keyValuePersistence().get(room).block(timeout)).isNull()
    }
}
