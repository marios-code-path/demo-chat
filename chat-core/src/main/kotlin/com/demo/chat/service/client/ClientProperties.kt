package com.demo.chat.service.client

interface ClientProperties<T> {
    fun getServiceConfig(serviceKey: String): T
}