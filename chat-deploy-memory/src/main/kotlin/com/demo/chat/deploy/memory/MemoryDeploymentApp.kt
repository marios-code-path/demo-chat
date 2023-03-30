package com.demo.chat.deploy.memory

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import reactor.core.publisher.Hooks

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"],
    proxyBeanMethods = false)
@Profile("exec-chat")
class MemoryDeploymentApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<MemoryDeploymentApp>(*args)
        }
    }


    @Bean
    //@ConditionOn....
    fun <T> chatUserDetailsService(
        persist: UserPersistence<T>,
        index: UserIndexService<T, IndexSearchRequest>,
        auth: AuthenticationService<T>,
        authZ: AuthorizationService<T, String>,
    ): ChatUserDetailsService<T, IndexSearchRequest> = ChatUserDetailsService(
        persist, index, auth, authZ
    ) { name -> IndexSearchRequest(UserIndexService.HANDLE, name, 100) }


    @EventListener
    fun readyListener(event: ApplicationReadyEvent) {
        println("In-Memory Deployment Started.")
    }
}