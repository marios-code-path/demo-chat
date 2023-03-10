package com.demo.chat.shell.shell

import com.demo.chat.shell.ShellStateConfiguration
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
        AttributedString("chat-init:${getLoggedIn()} :> ")
}