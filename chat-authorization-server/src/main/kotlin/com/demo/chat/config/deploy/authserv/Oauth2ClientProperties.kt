package com.demo.chat.config.deploy.authserv

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.oauth2.client")
open class Oauth2ClientProperties {
    var clientId: String = ""
    var id: String = ""
    var redirectUriPrefix: String = ""
    var additionalScopes: List<String> = listOf()
    var secret: String = ""
    var redirectUris: List<String> = listOf()
    var authorizationGrantTypes: List<String> = listOf()
    var clientAuthenticationMethods: List<String> = listOf()
    var requiresAuthorizationConcent: Boolean = true
}