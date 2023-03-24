package com.demo.chat.service.client

interface ClientFactory<C> {
    fun getClient(serviceKey: String): C
    fun serviceDestination(serviceKey: String): String
}