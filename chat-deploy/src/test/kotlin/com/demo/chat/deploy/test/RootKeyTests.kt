package com.demo.chat.deploy.test

import com.demo.chat.deploy.bootstrap.*
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.anyBoolean
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono

class LongRootKeyTests : RootKeyTests<Long>(LongUtil(), TestLongKeyGenerator(), TestLongKeyService())

@Disabled
@ExtendWith(MockitoExtension::class)
open class RootKeyTests<T>(
    private val typeUtil: TypeUtil<T>,
    private val keyGenerator: IKeyGenerator<T>,
    private val keyService: IKeyService<T>
) {

    @Mock
    private lateinit var userService: ChatUserService<T>

    @Mock
    private lateinit var authorizationService: AuthorizationService<T, AuthMetadata<T>>

    @BeforeEach
    fun setUp() {
        BDDMockito
            .given(userService.addUser(anyObject()))
            .willReturn(Mono.defer {
                Mono.just(keyGenerator.nextKey())
            })

        BDDMockito
            .given(authorizationService.authorize(anyObject(), anyBoolean()))
            .willReturn(Mono.empty())
    }


    @Test
    fun `test root key`() {
        Hooks.onOperatorDebug()
        val properties = BootstrapProperties(
            BootstrapRoles(arrayOf("CREATE", "READ"), "*", arrayOf<RoleDefinition>(
                RoleDefinition("Admin", "Admin", "*"),
                RoleDefinition( "User", "User", "READ"),
                RoleDefinition("User", "MessageTopic", "READ")
            )),
            mapOf(
                Pair("Admin", BootstrapUser("Admin", "AdminUser", "http://foo.bar.img")),
                Pair("Anon", BootstrapUser("Anon", "Anonymous", "http://anon.img"))
            )
        )

        val service = BootstrappingService(userService, authorizationService, properties, keyService, typeUtil)
        val complete = service.bootstrapUsers()

        Assertions
            .assertThat(complete)
            .isNotNull
            .hasSizeGreaterThan("Root Keys: \n".length)

        println(complete)
    }
}