package com.demo.chat.init.shell

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import org.jline.utils.AttributedString
import org.springframework.context.ApplicationEvent
import org.springframework.context.annotation.Bean
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class PromptProvider<T>(val typeUtil: TypeUtil<T>) : PromptProvider {
    var loggedInUser: Optional<String> = Optional.empty()

    override fun getPrompt(): AttributedString =
        AttributedString("chat-init:${loggedInUser.orElse("anonymous")} :> ")

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