package com.demo.chat.config.deploy.authserv

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope

@ConfigurationProperties("app.oauth2.client")
@RefreshScope
open class Oauth2ClientProperties {
    var id: String = ""
    var key: String = ""
    var redirectUriPrefix: String = ""
    var additionalScopes: List<String> = listOf()
    var secret: String = ""
    var redirectUris: List<String> = listOf()
}