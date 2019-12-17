package com.demo.chat.controller.rsocket

import com.demo.chat.domain.*
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.stereotype.Controller
import java.util.*

@Controller
class UserIndexRSocket(t: UserIndexService) : IndexServiceController<UUID, User, Map<String, String>, Map<String, String>>(t)

@Controller
class MessageIndexRSocket(t: MessageIndexService) : IndexServiceController<UUID, TextMessage, Map<String, String>, Map<String, String>>(t)

@Controller
class RoomIndexRSocket(t: TopicIndexService) : IndexServiceController<UUID, MessageTopic, Map<String, String>, Map<String, String>>(t)

@Controller
class MembershipIndexRSocket(t: MembershipIndexService) : IndexServiceController<UUID, Membership<UUID>, Map<String, String>, Map<String, String>>(t)