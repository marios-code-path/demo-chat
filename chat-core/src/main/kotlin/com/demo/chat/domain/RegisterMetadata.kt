package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeName

/**
 * Check whether [name] can own its register room namespace.
 * [allowedNames] contains rooms from the same register attempt.
 * The collision check excludes these rooms from its forward prefix scan.
 * Register routes must send denial and backend failure as RSocket error signals.
 * They must never convert these errors to CLEAR, NOT_FOUND, or NO_OWNER.
 */
@JsonTypeName("RegisterCollisionRequest")
data class RegisterCollisionRequest(
    val name: String,
    val allowedNames: Set<String>,
) : RequestResponse<Any>()

/** The collision verdict after a completed read. */
enum class RegisterCollisionStatus { CLEAR, ROOM_HELD, REGISTER_HELD }

/**
 * Name the conflicting room for [RegisterCollisionStatus.ROOM_HELD].
 * Name the conflicting register for [RegisterCollisionStatus.REGISTER_HELD].
 * Send both nullable fields for every verdict.
 */
data class RegisterCollisionResult(
    val status: RegisterCollisionStatus,
    val roomName: String?,
    val registerName: String?,
)

/** A room name matches one room, no room, or more than one room after a completed read. */
enum class RegisterRoomStatus { FOUND, NOT_FOUND, AMBIGUOUS_NAME }

/** Send [roomId] for [RegisterRoomStatus.FOUND]. Send null for the other verdicts. */
data class RegisterRoomResult(
    val status: RegisterRoomStatus,
    val roomId: String?,
)

/** A room has one live entity owner, no owner, or more than one distinct owner after a completed read. */
enum class RegisterOwnerStatus { FOUND, NO_OWNER, MULTIPLE_OWNERS }

/**
 * Send [ownerId] and [ownerHandle] for [RegisterOwnerStatus.FOUND].
 * Send null for both fields for the other verdicts.
 * A route must send the RSocket AUTHORIZATION code for a denied read.
 * It must propagate a backend failure as an RSocket error signal.
 */
data class RegisterOwnerResult(
    val status: RegisterOwnerStatus,
    val ownerId: String?,
    val ownerHandle: String?,
)
