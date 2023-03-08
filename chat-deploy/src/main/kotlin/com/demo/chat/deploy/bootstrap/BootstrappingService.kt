package com.demo.chat.deploy.bootstrap

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Configuration
@ConditionalOnProperty("app.bootstrap.init")
@EnableConfigurationProperties(BootstrapProperties::class)
class BootstrappingService<T>(
    private val userService: ChatUserService<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val bootstrapProperties: BootstrapProperties,
    private val keyService: IKeyService<T>,
    private val typeUtil: TypeUtil<T>
) {

    private val knownRootKeys: Set<Class<*>> = setOf(
        User::class.java,
        Message::class.java,
        MessageTopic::class.java,
        TopicMembership::class.java,
        AuthMetadata::class.java,
        Anon::class.java,
        Admin::class.java
    )

    fun rootKeySummary(rootKeys: RootKeys<T>): String {
        val sb = StringBuilder()

        sb.append("Root Keys: \n")
        for (rootKey in knownRootKeys) {
            if (rootKeys.hasRootKey(rootKey))
                sb.append("${rootKey.simpleName}=${rootKeys.getRootKey(rootKey)}\n")
        }

        return sb.toString()
    }

    @Bean
    fun eventListener(root: RootKeys<T>): ApplicationListener<ApplicationStartedEvent> = ApplicationListener { evt ->
        println(rootKeySummary(root))
    }

    @Bean
    fun bootstrap(): RootKeys<T> {

        val rootKeys: RootKeys<T> = RootKeys()

        // Create key for each Domain Type
        Flux.just(
            User::class.java, Message::class.java, MessageTopic::class.java,
            TopicMembership::class.java, AuthMetadata::class.java
        )
            .flatMap { domain ->
                keyService.key(domain)
                    .doOnNext { key ->
                        rootKeys.addRootKey(domain, key)
                    }
            }
            .blockLast()

        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        if (!(bootstrapProperties.users.containsKey(Admin::class.java.simpleName) &&
                    bootstrapProperties.users.containsKey(Anon::class.java.simpleName))
        ) {
            throw RuntimeException("Admin and Anon users must be defined")
        }
        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

            val thisUserKey = Mono.from(
                userService.addUser(
                    UserCreateRequest(
                        thisUser.name,
                        thisUser.handle,
                        thisUser.imageUri
                    )
                )
            )
                .defaultIfEmpty(emptyKey)
                .onErrorResume {
                    userService
                        .findByUsername(ByStringRequest(thisUser.handle))
                        .map { u -> u.key }
                        .last()
                }
                ?.block()

            identityKeys[identity] = thisUserKey!!
        }

        val anonKey = identityKeys["Anon"]!!
        val adminKey = identityKeys["Admin"]!!

        rootKeys.addRootKey(Admin::class.java, adminKey)
        rootKeys.addRootKey(Anon::class.java, anonKey)

        val initialRoles: MutableSet<AuthMetadata<T>> = mutableSetOf()

        // get role definitions
        bootstrapProperties.roles.initialRoles.forEach { permission ->
            if (rootKeys.hasKey(permission.target) && rootKeys.hasKey(permission.user)) {
                initialRoles.add(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        rootKeys.getRootKey(permission.user)!!,
                        rootKeys.getRootKey(permission.target)!!,
                        permission.role,
                    )
                )
            } else {
                println("Missing root key for ${permission.user} or ${permission.target}")
            }
        }

        // set permissions
        Flux.fromIterable(initialRoles)
            .flatMap { authMeta ->
                authorizationService.authorize(authMeta, true)
            }.blockLast()

        return rootKeys
    }

}