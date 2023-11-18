package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.init.*
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.KnownRootKeys.Companion.knownRootKeys
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.service.init.RootKeyService
import com.demo.chat.domain.knownkey.RootKeysSupplier
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyBoolean
import com.demo.chat.test.anyObject
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono

@Disabled
@ExtendWith(MockitoExtension::class)
open class MockInitializationTests<T>(
    private val typeUtil: TypeUtil<T>,
    private val keyGenerator: IKeyGenerator<T>,
    private val keyService: IKeyService<T>
) {

    @Mock
    private lateinit var userService: ChatUserService<T>

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var authorizationService: AuthorizationService<T, AuthMetadata<T>>

    @Mock
    private lateinit var secretsStore: SecretsStore<T>

    @Mock
    private lateinit var kvStore: KeyValueStore<String, String>

    private val mapper = ObjectMapper()

    @Test
    fun `should create rootkeys and summary`() {
        val rootKeyCreator = RootKeysSupplier(keyService)
        val rootKeys = RootKeys<T>()

        rootKeys.merge(rootKeyCreator.get())
        val summary = RootKeys.rootKeySummary(rootKeys)

        Assertions
            .assertThat(summary)
            .isNotNull
            .hasSizeGreaterThan("Root Keys: \n".length)

        knownRootKeys.forEach {
            Assertions
                .assertThat(rootKeys.getMapOfKeyMap())
                .containsKey(it.simpleName)
        }
    }

    @Test
    fun `test user init`() {
        BDDMockito
            .given(secretsStore.addCredential(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(userService.addUser(anyObject()))
            .willReturn(Mono.defer {
                Mono.just(keyGenerator.nextKey())
            })

        BDDMockito
            .given(authorizationService.authorize(anyObject(), anyBoolean()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(passwordEncoder.encode(anyObject()))
            .willReturn("{noop}password")

        val rootKeys = RootKeys<T>()

        Hooks.onOperatorDebug()
        val properties = UserInitializationProperties(
            "noop",
            InitalRoles(
                arrayOf("CREATE", "READ"), "*", arrayOf<RoleDefinition>(
                    RoleDefinition("Admin", "Admin", "*"),
                    RoleDefinition("User", "User", "READ"),
                    RoleDefinition("User", "MessageTopic", "READ")
                )
            ),
            mapOf(
                Pair("Admin", UserDefinition("Admin", "AdminUser", "http://foo.bar.img","changeme")),
                Pair("Anon", UserDefinition("Anon", "Anonymous", "http://anon.img","_"))
            )
        )

        val rootKeyService = RootKeyService(kvStore, typeUtil,"rootKeys")
        val rootKeyCreator = RootKeysSupplier(keyService)
            .apply {
                rootKeys.merge(get())
            }

        InitialUsersService(userService, authorizationService, secretsStore, properties, passwordEncoder, typeUtil)
            .apply {
                rootKeys.merge(
                    initializeUsers(rootKeys)
                )
            }

        val summary = RootKeys.rootKeySummary(rootKeys)

        Assertions
            .assertThat(summary)
            .isNotNull
            .hasSizeGreaterThan("Root Keys: \n".length)

        Assertions
            .assertThat(summary)
            .contains("Admin")
            .contains("Anon")
    }
}