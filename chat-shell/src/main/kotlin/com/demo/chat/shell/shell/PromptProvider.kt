package com.demo.chat.shell.shell

import com.demo.chat.domain.TypeUtil
import com.demo.chat.shell.ShellStateConfiguration
import org.jline.utils.AttributedString
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class PromptProvider<T>(val typeUtil: TypeUtil<T>) : PromptProvider {

    fun getLoggedIn(): String =
        ShellStateConfiguration.simpleAuthToken
            .map { auth ->
                auth.name
            }
            .orElse("anonymous")

    override fun getPrompt(): AttributedString =
        AttributedString("chat-init:${getLoggedIn()} :> ")

}

class UserLoggedInEvent : EventObject("login") {

}
/**
 * @Component
 * public class CustomPromptProvider implements PromptProvider {
 *
 *         private ConnectionDetails connection;
 *
 *         @Override
 *         public AttributedString getPrompt() {
 *                 if (connection != null) {
 *                         return new AttributedString(connection.getHost() + ":>",
 *                                 AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
 *                 }
 *                 else {
 *                         return new AttributedString("server-unknown:>",
 *                                 AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
 *                 }
 *         }
 *
 *         @EventListener
 *         public void handle(ConnectionUpdatedEvent event) {
 *                 this.connection = event.getConnectionDetails();
 *         }
 * }
 */