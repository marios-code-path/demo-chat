package com.demo.chat.controller.rsocket

import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.springframework.stereotype.Controller
import java.util.*

@Controller
class RSocketUserPersistence(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

@Controller
class KeyPersistenceRSocket(t: KeyPersistence<UUID>) : PersistenceServiceController<UUID, Key<UUID>>(t)

@Controller
class RSocketMessagePersistence(t: PersistenceStore<UUID, Message<UUID, Any>>) : PersistenceServiceController<UUID, Message<UUID, Any>>(t)

@Controller
class TopicPersistenceRSocket(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

@Controller
class RSocketMembershipPersistence(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)