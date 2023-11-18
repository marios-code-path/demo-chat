package com.demo.chat.controller.webflux

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.composite.mapping.ChatMessageServiceRestMapping
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/message")
@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
class ChatMessageServiceController<T>(val beans: CompositeServiceBeans<T, String>) : ChatMessageServiceRestMapping<T>,
    ChatMessageService<T, String> by beans.messageService()
