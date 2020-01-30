package com.demo.chat.controller.rsocket

import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import java.util.*

@Profile("ctrl-persist-user")
@Controller
class RSocketUserPersistence(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

@Profile("ctrl-persist-key")
@Controller
class KeyPersistenceRSocket(t: KeyPersistence<UUID>) : PersistenceServiceController<UUID, Key<UUID>>(t)

@Profile("ctrl-persist-message")
@Controller
class RSocketMessagePersistence(t: PersistenceStore<UUID, Message<UUID, Any>>) : PersistenceServiceController<UUID, Message<UUID, Any>>(t)

@Profile("ctrl-persist-topic")
@Controller
class TopicPersistenceRSocket(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

@Profile("ctrl-persist-membership")
@Controller
class RSocketMembershipPersistence(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)