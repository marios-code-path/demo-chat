package com.demo.chat.domain

open class ChatException(msg: String, cause: Throwable? = null) : Exception(msg, cause)
object AccessDeniedException : ChatException("Access Denied")
object DuplicateException : ChatException("Object already exists")
object NotFoundException : ChatException("Object not Found")
/** The `Anon` key cannot be a room member. The owner decided this on 2026-10-02. See `CHAT-mfveaecc`. */
object AnonymousJoinException : ChatException("An anonymous caller cannot join a room.")
class InvalidRecallRequestException(message: String) : ChatException(message)
open class AuthenticationException(msg: String) : Exception(msg)
object UsernamePasswordAuthenticationException : AuthenticationException("Invalid Credentials")