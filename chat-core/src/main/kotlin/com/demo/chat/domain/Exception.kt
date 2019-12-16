package com.demo.chat.domain

import java.util.*

open class ChatException(msg: String) : Exception(msg)
object DuplicateUserException : ChatException("User already exists")
object TopicNotFoundException : ChatException("Topic not Found")


open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid Credentials")

open class IndexException(msg: String) : ChatException(msg)
class ChatIndexException(id: UUID) : IndexException("Unable to access index for item $id")