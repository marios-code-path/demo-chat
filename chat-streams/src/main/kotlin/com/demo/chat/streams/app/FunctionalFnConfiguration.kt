package com.demo.chat.streams.app

import com.demo.chat.streams.functions.UserCreateRequest
import com.demo.chat.domain.User
import com.demo.chat.streams.functions.UserFunctions
import org.springframework.cloud.function.context.FunctionRegistration
import org.springframework.cloud.function.context.FunctionType
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext


open class FunctionalFnConfiguration<T, Q>(
    val userFn: UserFunctions<T, Q>
) : ApplicationContextInitializer<GenericApplicationContext> {

    override fun initialize(context: GenericApplicationContext) {
        context.registerBean("userStream", FunctionRegistration::class.java,
            {
                FunctionRegistration(userFn::userCreateFunction)
                    .type(FunctionType.from(UserCreateRequest::class.java).to(User::class.java))
            })
    }

}