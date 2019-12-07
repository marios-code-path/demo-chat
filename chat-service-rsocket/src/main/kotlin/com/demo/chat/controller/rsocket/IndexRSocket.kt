package com.demo.chat.controller.rsocket

import com.demo.chat.domain.*
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.RoomIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.stereotype.Controller

@Controller
class UserIndexRSocket(t: UserIndexService) : IndexServiceController<UserKey, User, Map<String, String>, Map<String, String>>(t)

@Controller
class MessageIndexRSocket(t: MessageIndexService) : IndexServiceController<MessageKey, TextMessage, Map<String, String>, Map<String, String>>(t)

@Controller
class RoomIndexRSocket(t: RoomIndexService) : IndexServiceController<TopicKey, EventTopic, Map<String, String>, Map<String, String>>(t)

@Controller
class MembershipIndexRSocket(t: MembershipIndexService) : IndexServiceController<UUIDKey, TopicMembership, Map<String, String>, Map<String, String>>(t)