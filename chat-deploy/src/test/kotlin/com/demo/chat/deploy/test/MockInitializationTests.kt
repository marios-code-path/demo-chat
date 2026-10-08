package com.demo.chat.deploy.test

import com.demo.chat.test.key.TestKeys

import com.demo.chat.config.deploy.init.*
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.init.InitialUsersService
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
import reactor.core.publisher.Flux
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


    private val mapper = ObjectMapper()

    @Test
    fun `should load rootkeys and summary`() {
        val rootKeys = RootKeys<T>()
        rootKeys.loadDomains(ChatDomain.entries.associateWith { TestKeys.key(keyGenerator.nextId()) })
        val summary = RootKeys.rootKeySummary(rootKeys)

        Assertions
            .assertThat(summary)
            .isNotNull
            .hasSizeGreaterThan("Root Keys: \n".length)

        ChatDomain.entries.forEach {
            Assertions
                .assertThat(summary)
                .contains(it.wireName)
        }
    }

    private fun oneUser() = UserInitializationProperties(
        "noop",
        InitalRoles(arrayOf<RoleDefinition>()),
        mapOf(Pair("Admin", UserDefinition("Admin", "AdminUser", "http://foo.bar.img", "changeme"))),
    )

    private fun loadedRoots() = RootKeys<T>().apply {
        loadDomains(ChatDomain.entries.associateWith { TestKeys.key(keyGenerator.nextId()) })
    }

    // No placeholder key stands in for a user. See CHAT-avduuqwp, C16.
    @Test
    fun `initialization fails when a user create answers empty`() {
        BDDMockito.given(userService.addUser(anyObject())).willReturn(Mono.empty())

        Assertions
            .assertThatThrownBy {
                InitialUsersService(userService, authorizationService, secretsStore, oneUser(), passwordEncoder, typeUtil)
                    .initializeUsers(loadedRoots())
            }
            .hasMessageContaining("Cannot initialize user AdminUser")
    }

    @Test
    fun `initialization fails when a user cannot be created or found`() {
        BDDMockito.given(userService.addUser(anyObject())).willReturn(Mono.error(IllegalStateException("exists")))
        BDDMockito.given(userService.findByUsername(anyObject())).willReturn(Flux.empty())

        Assertions
            .assertThatThrownBy {
                InitialUsersService(userService, authorizationService, secretsStore, oneUser(), passwordEncoder, typeUtil)
                    .initializeUsers(loadedRoots())
            }
            .hasMessageContaining("Cannot initialize user AdminUser")
    }

    @Test
    fun `test user init`() {
        BDDMockito
            .given(secretsStore.addCredential(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(userService.addUser(anyObject()))
            .willReturn(Mono.defer {
                Mono.just(TestKeys.key(keyGenerator.nextId()))
            })

        BDDMockito
            .given(authorizationService.authorize(anyObject(), anyBoolean()))
            .willReturn(Mono.empty())

        // The store holds no grant, so the start seeds every initial grant. See CHAT-ghwtzgjp.
        BDDMockito
            .given(authorizationService.getStoredGrants(anyObject(), anyObject()))
            .willReturn(Flux.empty())

        BDDMockito
            .given(passwordEncoder.encode(anyObject()))
            .willReturn("{noop}password")

        val rootKeys = RootKeys<T>()

        Hooks.onOperatorDebug()
        val properties = UserInitializationProperties(
            "noop",
            InitalRoles(
                arrayOf<RoleDefinition>(
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

        rootKeys.loadDomains(ChatDomain.entries.associateWith { TestKeys.key(keyGenerator.nextId()) })

        InitialUsersService(userService, authorizationService, secretsStore, properties, passwordEncoder, typeUtil)
            .apply {
                initializeUsers(rootKeys)
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
