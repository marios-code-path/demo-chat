package com.demo.chat.ui

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Controller
class AuthorizeController {

    @GetMapping("/authorize", params = ["grant_type=authorization_code"])
    fun authorizationCodeGrant(model: Model,
        @RegisteredOAuth2AuthorizedClient("chat-client-authorization-code")
        authorizedClient: OAuth2AuthorizedClient
    ): Mono<String> {
        val messages = arrayOf("clientA", "clientB", "clientC34", authorizedClient.accessToken.tokenValue)
        model.addAttribute("users", messages)

        return Mono.just("index")
    }

    @GetMapping("/authorized", params = [OAuth2ParameterNames.ERROR])
    fun authorizationFailed(model: Model, exchange: ServerWebExchange): Mono<String> {
        val errorCode = exchange.request.queryParams.getFirst(OAuth2ParameterNames.ERROR)
        if (errorCode != null) {
            model.addAttribute("error",
                OAuth2Error(
                    errorCode,
                    exchange.request.queryParams.getFirst(OAuth2ParameterNames.ERROR_DESCRIPTION),
                    exchange.request.queryParams.getFirst(OAuth2ParameterNames.ERROR_URI)
                ))
        }

        return Mono.just("index")
    }

    @GetMapping("/authorize", params = ["grant_type=client_credentials"])
    fun clientCredentialsGrant(model: Model): Mono<String> {
        model.addAttribute("users", listOf("user1", "user2", "user3"))
        return Mono.just("index")
    }
}