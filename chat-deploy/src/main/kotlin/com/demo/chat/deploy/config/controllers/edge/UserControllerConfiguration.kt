package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class UserControllerConfiguration {

    @Controller
    @MessageMapping("edge.user")
    class UserController<T, Q>(
            persistence: PersistenceStore<T, User<T>>,
            index: IndexService<T, User<T>, Q>,
            reqs: RequestToQueryConverter<T, Q>,
    ) : UserServiceController<T, Q>(persistence, index, reqs::userHandleToQuery)
}
