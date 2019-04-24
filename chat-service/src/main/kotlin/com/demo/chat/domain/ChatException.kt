package com.demo.chat.domain

open class ChatException(msg: String) : Exception(msg)
object UserNotFoundException : ChatException("User not Found")
object RoomNotFoundException : ChatException("Room not Found")

open class RoomOperationException(msg: String) : Exception(msg)
object RoomJoinLeaveException : RoomOperationException("Unable to leave/join this Room")


