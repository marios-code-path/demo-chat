package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.ByHandleRequest
import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Function

open class UserControllerConfiguration {

    @ConditionalOnProperty(prefix = "app.edge", name = ["user"])
    @Controller
    @MessageMapping("user")
    class UserController<T, Q>(
            persistence: UserPersistence<T>,
            index: UserIndexService<T, Q>,
            queryFunction: Function<ByHandleRequest, Q>,
    ) : UserServiceController<T, Q>(persistence, index, queryFunction)
}
