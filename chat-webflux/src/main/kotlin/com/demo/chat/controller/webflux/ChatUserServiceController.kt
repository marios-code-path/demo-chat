package com.demo.chat.controller.webflux

import com.demo.chat.controller.webflux.composite.mapping.ChatUserServiceRestMapping
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.config.CompositeServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user")
@ConditionalOnProperty(prefix = "app.controller", name = ["user"])
class ChatUserServiceController<T>(s: CompositeServiceBeans<T, String>) : ChatUserServiceRestMapping<T>,
    ChatUserService<T> by s.userService()