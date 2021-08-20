package com.demo.chat.deploy.app

import org.springframework.boot.runApplication

// ensure configuration of consul is specific to consul only
class CoreDeployer {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CoreDeployer>(*args)
        }
    }

}