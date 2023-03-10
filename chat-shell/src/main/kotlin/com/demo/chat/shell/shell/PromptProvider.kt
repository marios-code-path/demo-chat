package com.demo.chat.shell.shell

import com.demo.chat.shell.ShellStateConfiguration
import org.jline.utils.AttributedString
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component

@Component
class PromptProvider<T>() : PromptProvider {

    fun getLoggedIn(): String =
        ShellStateConfiguration.simpleAuthToken
            .map { auth ->
                auth.name
            }
            .orElse("anonymous")

    override fun getPrompt(): AttributedString =
        AttributedString("chat-init:${getLoggedIn()} :> ")

}