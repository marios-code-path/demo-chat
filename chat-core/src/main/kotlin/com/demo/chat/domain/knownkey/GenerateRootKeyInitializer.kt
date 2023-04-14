package com.demo.chat.domain.knownkey

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.core.IKeyGenerator

class GenerateRootKeyInitializer <T>(private val keyGen: IKeyGenerator<T> ) {

    fun initRootKeys(rootKeys: RootKeys<T>) {
        rootKeys.addRootKey(MessageTopic::class.java, keyGen.nextKey())
        rootKeys.addRootKey(Message::class.java, MessageKey.create(keyGen.nextId(), keyGen.nextId(), keyGen.nextId()))
        rootKeys.addRootKey(User::class.java, keyGen.nextKey())
        rootKeys.addRootKey(TopicMembership::class.java, keyGen.nextKey())
        rootKeys.addRootKey(AuthMetadata::class.java, keyGen.nextKey())
        rootKeys.addRootKey(Admin::class.java, keyGen.nextKey())
        rootKeys.addRootKey(Anon::class.java, keyGen.nextKey())
    }
}