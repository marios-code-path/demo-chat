package com.demo.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication(
    scanBasePackages = ["com.demo.chat.config"],
    proxyBeanMethods = false
)
class ChatApp

/**
 * The entry point, in the class `com.demo.chat.ChatAppKt`. It is top level, not
 * in a companion object. A native image reads the class of this frame to find
 * its AOT initializer. See `docs/NATIVE-IMAGE.md` and `CHAT-arcqfjuc`.
 */
fun main(args: Array<String>) {
    Hooks.onOperatorDebug()
    runApplication<ChatApp>(*args)
}
