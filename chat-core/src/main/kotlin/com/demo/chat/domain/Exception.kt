package com.demo.chat.domain

open class ChatException(msg: String) : Exception(msg)
object DuplicateUserException : ChatException("User already exists")
object TopicNotFoundException : ChatException("Topic not Found")


open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid Credentials")

open class IndexException(msg: String) : ChatException(msg)
