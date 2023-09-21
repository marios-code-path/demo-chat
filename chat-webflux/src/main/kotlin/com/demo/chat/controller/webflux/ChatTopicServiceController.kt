package com.demo.chat.controller.webflux

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.composite.mapping.ChatTopicServiceRestMapping
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/topic")
class ChatTopicServiceController<T>(private val beans: CompositeServiceBeans<T, String>) : ChatTopicServiceRestMapping<T>,
    ChatTopicService<T, String> by beans.topicService()