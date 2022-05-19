package com.demo.chat.streams.integration

import com.demo.chat.service.SubscriptionDirectory
import org.springframework.integration.channel.PublishSubscribeChannel
import java.util.concurrent.ConcurrentHashMap

class IntegrationSubscriptionDirectory<T> : SubscriptionDirectory<T> {

    val channels: MutableMap<T, PublishSubscribeChannel> = ConcurrentHashMap()

    override fun getSubscribersFor(id: T): Collection<T> {
        TODO("Not yet implemented")
    }

    override fun subscribeTo(left: T, right: T) {
        if(! channels.containsKey(right))
            channels[right] = PublishSubscribeChannel()

    }

    override fun unsubscribe(left: T, right: T) {
        TODO("Not yet implemented")
    }
}