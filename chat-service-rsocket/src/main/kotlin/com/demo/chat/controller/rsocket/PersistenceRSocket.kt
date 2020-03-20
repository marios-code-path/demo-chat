package com.demo.chat.controller.rsocket

import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*
import java.util.*

class KeyServiceRSocket(t: IKeyService<UUID>) : KeyServiceController<UUID>(t)

class UserPersistenceRSocket(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

class MessagePersistenceRSocket(t: PersistenceStore<UUID, Message<UUID, Any>>) : PersistenceServiceController<UUID, Message<UUID, Any>>(t)

class TopicPersistenceRSocket(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

class MembershipPersistenceRSocket(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)