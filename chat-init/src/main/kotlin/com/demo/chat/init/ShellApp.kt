package com.demo.chat.init

import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.secure.rsocket.UnprotectedConnection
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import reactor.core.publisher.Mono

@SpringBootApplication
@EnableConfigurationProperties(RSocketClientProperties::class, BootstrapProperties::class)
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    UnprotectedConnection::class
)
@EnableGlobalMethodSecurity(securedEnabled = true)
class ShellApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ShellApp>(*args)
        }
    }
}