package com.demo.chat.shell.components

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import org.jline.utils.AttributedString
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component

@Component
class ShellPromptProvider() : PromptProvider {

    fun getLoggedIn(): String =
        ShellStateConfiguration.loginMetadata
            .map { auth -> auth.username }
            .orElse("anonymous")

    override fun getPrompt(): AttributedString =
        AttributedString("chat-shell:${getLoggedIn()} :> ")
}