package com.demo.chat.controller.rsocket

import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.springframework.stereotype.Controller

@Controller
class RSocketUserPersistence(t: UserPersistence) : PersistenceServiceController<User>(t)

@Controller
class RSocketKeyPersistence(t: KeyPersistence) : PersistenceServiceController<UUIDKey>(t)

@Controller
class RSocketMessagePersistence(t: TextMessagePersistence) : PersistenceServiceController<TextMessage>(t)

@Controller
class RSocketRoomPersistence(t: RoomPersistence) : PersistenceServiceController<EventTopic>(t)

@Controller
class RSocketMembershipPersistence(t: MembershipPersistence) : PersistenceServiceController<Membership<UUIDKey>>(t)