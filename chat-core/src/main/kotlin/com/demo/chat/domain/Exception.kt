package com.demo.chat.domain

open class ChatException(msg: String) : Exception(msg)
object DuplicateException : ChatException("Object already exists")
object NotFoundException : ChatException("Object not Found")
open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid Credentials")