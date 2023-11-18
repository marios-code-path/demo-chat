package com.demo.chat.test.config

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import org.mockito.BDDMockito

class TestCompositeServiceBeans<T, V> : CompositeServiceBeans<T, V> {

    val mockUserBean:ChatUserService<T> = BDDMockito.mock(ChatUserService::class.java)
            as? ChatUserService<T> ?: throw ClassCastException("Unable to cast mock to ChatUserService<T>")

    val mockMessageBean:ChatMessageService<T, V> = BDDMockito.mock(ChatMessageService::class.java) as ChatMessageService<T, V>

    val mockTopicBean:ChatTopicService<T, V> = BDDMockito.mock(ChatTopicService::class.java) as ChatTopicService<T, V>

    override fun messageService(): ChatMessageService<T, V> = mockMessageBean

    override fun topicService(): ChatTopicService<T, V> = mockTopicBean

    override fun userService(): ChatUserService<T> = mockUserBean

}