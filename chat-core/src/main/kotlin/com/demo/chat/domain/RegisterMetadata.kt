package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("RegisterCollisionRequest")
data class RegisterCollisionRequest(
    val name: String,
    val allowedNames: Set<String>,
) : RequestResponse<Any>()

enum class RegisterCollisionStatus { CLEAR, ROOM_HELD, REGISTER_HELD }

data class RegisterCollisionResult(
    val status: RegisterCollisionStatus,
    val roomName: String?,
    val registerName: String?,
)

enum class RegisterRoomStatus { FOUND, NOT_FOUND, AMBIGUOUS_NAME }

data class RegisterRoomResult(
    val status: RegisterRoomStatus,
    val roomId: String?,
)

enum class RegisterOwnerStatus { FOUND, NO_OWNER, MULTIPLE_OWNERS }

data class RegisterOwnerResult(
    val status: RegisterOwnerStatus,
    val ownerId: String?,
    val ownerHandle: String?,
)
