package com.demo.chat.deploy.test.init

import com.demo.chat.config.deploy.init.InitalRoles
import com.demo.chat.config.deploy.init.RoleDefinition
import com.demo.chat.config.deploy.init.UserDefinition
import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.anyObject
import com.demo.chat.test.key.TestKeys
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The scaffolding that both initial-user test classes share.
 *
 * The user service is a mock. **The secrets store and the authorization store
 * are real maps**, so a test reads what the service actually wrote. See
 * `CHAT-werokcbb` and `CHAT-ghwtzgjp`.
 *
 * The user service answers one key per handle. So a second start finds the
 * users of the first start, as a store that reloads its user index does.
 */
class InitialUsersFixture(val encoder: PasswordEncoder = Mockito.mock(PasswordEncoder::class.java)) {

    private val keyGenerator = TestLongKeyGenerator()

    @Suppress("UNCHECKED_CAST")
    val userService: ChatUserService<Long> =
        Mockito.mock(ChatUserService::class.java) as ChatUserService<Long>

    val authorizationService = RecordingAuthorizationService()

    val secretsStore = RecordingSecretsStore()

    private val userKeys: MutableMap<String, Key<Long>> = mutableMapOf()

    init {
        // A created user answers the key of its handle. The same handle gives
        // the same key at every start. Find answers nothing, so a failure stays
        // visible.
        BDDMockito.given(userService.addUser(anyObject())).willAnswer { call ->
            val request = call.getArgument<UserCreateRequest>(0)
            Mono.just(userKeys.getOrPut(request.handle) { TestKeys.key(keyGenerator.nextId()) })
        }
        BDDMockito.given(userService.findByUsername(anyObject())).willReturn(Flux.empty())
    }

    /** The production service under test, wired to every mock of this fixture. */
    fun service(properties: UserInitializationProperties) = InitialUsersService(
        userService, authorizationService, secretsStore, properties, encoder, LongUtil()
    )

    /** A root key set that holds every domain and no identity. */
    fun roots(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { TestKeys.key(keyGenerator.nextId()) })
    }

    companion object {
        /** Properties that name [users], with no role. */
        fun properties(vararg users: Pair<String, UserDefinition>) = UserInitializationProperties(
            "noop",
            InitalRoles(arrayOf<RoleDefinition>()),
            users.toMap(),
        )

        /** One initial user definition. The password defaults to blank. */
        fun user(name: String, handle: String, password: String = "") =
            UserDefinition(name, handle, "http://$handle.img", password)
    }
}

/**
 * A secrets store over a map.
 *
 * It survives two calls to `initializeUsers`, so a test can read what a second
 * start wrote over what the first start wrote.
 */
class RecordingSecretsStore : SecretsStore<Long> {

    private val rows: MutableMap<Key<Long>, String> = linkedMapOf()

    /** The credential text for one key, or null when no call wrote one. */
    fun stored(key: Key<Long>): String? = rows[key]

    override fun getStoredCredentials(key: Key<Long>): Mono<String> =
        Mono.justOrEmpty(rows[key])

    override fun addCredential(keyCredential: KeyCredential<Long>): Mono<Void> {
        rows[keyCredential.key] = keyCredential.data
        return Mono.empty()
    }

    override fun compareSecret(keyCredential: KeyCredential<Long>): Mono<Boolean> =
        Mono.just(rows[keyCredential.key] == keyCredential.data)
}
