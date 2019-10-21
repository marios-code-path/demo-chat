package com.demo.chat.domain

import java.util.*

open class ChatException(msg: String) : Exception(msg)
object DuplicateUserException : ChatException("User already exists")
object UserNotFoundException : ChatException("User not Found")
object RoomNotFoundException : ChatException("Room not Found")
object SessionClosedException : ChatException("Feed Session has Closed")

open class RoomOperationException(msg: String) : Exception(msg)
object RoomJoinLeaveException : RoomOperationException("Unable to leave/join this Room")

open class PermissionsException(msg: String) : Exception(msg)
object InsufficientPermissionException : PermissionsException("Insufficient Permission")

open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid username / password")

open class IndexException(msg: String) : ChatException(msg)
class ChatIndexException(id: UUID) : IndexException("Unable to access index for item $id")