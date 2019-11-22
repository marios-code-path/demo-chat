package com.demo.chat.service

import com.demo.chat.domain.*
import com.demo.chat.controllers.service.IndexServiceController


class UserIndexRSocket(t: UserIndexService) : IndexServiceController<UserKey, User, Map<String, String>, Map<String, String>>(t)
class MessageIndexRSocket(t: MessageIndexService) : IndexServiceController<MessageKey, TextMessage, Map<String, String>, Map<String, String>>(t)
class RoomIndexRSocket(t: RoomIndexService) : IndexServiceController<RoomKey, Room, Map<String, String>, Map<String, String>>(t)
class MembershipIndexRSocket(t: MembershipIndexService) : IndexServiceController<EventKey, RoomMembership, Map<String, String>, Map<String, String>>(t)