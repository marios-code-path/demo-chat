package com.demo.chat.ui

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DefaultController {
    @GetMapping("/")
    fun root(): String {
        return "redirect:/index"
    }

    @GetMapping("/index")
    fun index(): String {
        return "index"
    }
}