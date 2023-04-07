package com.demo.chat.deploy.authserv

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus

//@Controller
//@Configuration
class AppController {
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(ex: Exception): String {
        ex.printStackTrace()
        return "ERROR :  ${ex.message}"
    }
//
//    @GetMapping("/error")
//    fun error(): String {
//        println("ERROR")
//        return "error"
//    }

}