package com.demo.chat.domain.command

import com.demo.chat.domain.ChatException

class RequestConflictException(val requestId: String) :
    ChatException("The request $requestId already names a different message.")

class SenderMismatchException(val sender: Any?, val user: Any?) :
    ChatException("The sender $sender is not the authenticated user $user. Stage 1 does not allow another sender.")

class SubmitterUnavailableException :
    ChatException("No submitter identity exists. A message submission needs app.service.composite.auth=true.")

class InvalidRequestIdException(reason: String) : ChatException("The request ID is not valid. $reason")

class CommandPendingException(val commandId: String) :
    ChatException("Command $commandId is still pending. Read its status with the command ID.")

class CommandIncompleteException(val commandId: String, val failed: Set<BackendId>) :
    ChatException("Command $commandId failed in ${failed.joinToString { it.name }}.")

/** A handler throws this when an effect may have happened. The runtime records an uncertain result. */
class UncertainOutcomeException(message: String, cause: Throwable? = null) : ChatException(message, cause)
