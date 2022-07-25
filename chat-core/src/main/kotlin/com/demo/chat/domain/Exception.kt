package com.demo.chat.domain

open class ChatException(msg: String) : Exception(msg)
object DuplicateException : ChatException("User already exists")
object NotFoundException : ChatException("Topic not Found")
open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid Credentials")