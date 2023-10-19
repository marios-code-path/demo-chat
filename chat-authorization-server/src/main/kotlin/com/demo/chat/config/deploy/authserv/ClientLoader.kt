package com.demo.chat.config.deploy.authserv

import com.demo.chat.auth.client.RegisteredClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.UrlResource
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.io.File


@Profile("client-init")
@Configuration
class ClientInitializer(val repo: RegisteredClientRepository,
                        val mapper: ObjectMapper) {

    @Bean
    fun loadClient(): ApplicationRunner =
        ApplicationRunner { args ->
            if(args.containsOption("clientpath")) {
                val clientPath = args.getOptionValues("clientpath")[0]

                val resource = if(clientPath.startsWith("classpath:")) {
                    ClassPathResource(clientPath.substring(10))
                } else {
                    UrlResource(File(clientPath).toURI().toURL())
                }

                val clientProps = mapper.readValue(resource.inputStream, Oauth2ClientProperties::class.java)
                saveClient(clientProps)
            }
        }

    fun saveClient(clientProperties: Oauth2ClientProperties) {
        val client = RegisteredClientFactory(clientProperties)()

        val oldClient = repo.findByClientId(client.clientId)

        if(oldClient==null)
            repo.save(client)
    }
}