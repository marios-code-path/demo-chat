package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.domain.*

class RootKeyPopulator <T>(private val keyGen: IKeyGenerator<T> ) {

    fun populateRootKeys(rootKeys: RootKeys<T>) {
        rootKeys.addRootKey(MessageTopic::class.java, Key.funKey(keyGen.nextKey()))
        rootKeys.addRootKey(Message::class.java, MessageKey.create(keyGen.nextKey(), keyGen.nextKey(), keyGen.nextKey()))
        rootKeys.addRootKey(User::class.java, Key.funKey(keyGen.nextKey()))
        rootKeys.addRootKey(TopicMembership::class.java, Key.funKey(keyGen.nextKey()))
        rootKeys.addRootKey(AuthMetadata::class.java, Key.funKey(keyGen.nextKey()))
        rootKeys.addRootKey(Role::class.java, Key.funKey(keyGen.nextKey()))
    }
}