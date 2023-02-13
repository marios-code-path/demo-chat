package com.demo.chat.init.commands

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.shell.standard.ShellComponent

@ShellComponent
@Profile("shell")
class InitOnceCommands<T>(
    userService: ChatUserService<T>,
    authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    authenticationManager: AuthenticationManager,
    bootstrapProperties: BootstrapProperties,
    typeUtil: TypeUtil<T>
) {


}