package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.shell.deploy.ShellStateConfiguration.Companion.loggedInUser
import com.demo.chat.config.shell.deploy.ShellStateConfiguration.Companion.loginMetadata
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import reactor.core.publisher.Mono
import java.util.*
import kotlin.system.exitProcess

@Component
@Profile("shell")
class LoginCommands<T>(
    private val compositeServices: CompositeServiceBeans<T, String>,
    val rootKeys: RootKeys<T>,
    typeUtil: TypeUtil<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    val userService: ChatUserService<T> = compositeServices.userService()
    fun bye(): Unit {
        exitProcess(0)
    }
    fun rootKeys(): String {
        return RootKeys.rootKeySummary(rootKeys)
    }
    fun whoami(): String? {

        return userService
            .findByUserId(ByIdRequest(identity("_")))
            .map { user -> "${user.key.id} : ${user.handle} | ${user.name} | ${user.imageUri}" }
            .switchIfEmpty(Mono.error(NotFoundException))
            .block()
    }
    fun login(
        username: String,
        password: String
    ) {
        try {
            loginMetadata = Optional.of(UsernamePasswordMetadata(username, password))
            val user = userService.findByUsername(ByStringRequest(username)).blockLast()!!

            loggedInUser = Optional.of(user.key.id as Any)
            // load authentication into clients
        } catch (e: Exception) {
            println("Authentication failed :" + e.message)

            loginMetadata = Optional.empty()
            loggedInUser = Optional.empty()

            throw e
        }
    }
}