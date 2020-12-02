package com.demo.chat.deploy.app

import org.springframework.boot.runApplication

class CoreDeployer {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CoreDeployer>(*args)
        }
    }

}