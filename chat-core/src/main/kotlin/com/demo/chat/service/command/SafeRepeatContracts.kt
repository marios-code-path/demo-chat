package com.demo.chat.service.command

import com.demo.chat.domain.command.BackendId

data class SafeRepeatContract(val name: String, val version: Int)

/**
 * The safe-repeat contracts that Stage 1 supports. Startup validates the
 * declaration of each active handler. CI proves each contract with tests.
 */
object SafeRepeatContracts {
    val MESSAGE_PERSISTENCE = SafeRepeatContract("message-persistence", 1)
    val MESSAGE_INDEX = SafeRepeatContract("message-index", 1)
    val MESSAGE_VECTOR = SafeRepeatContract("message-vector", 1)
    val MESSAGE_PUBSUB = SafeRepeatContract("message-pubsub", 1)

    val SUPPORTED: Map<BackendId, SafeRepeatContract> = mapOf(
        BackendId.PERSISTENCE to MESSAGE_PERSISTENCE,
        BackendId.INDEX to MESSAGE_INDEX,
        BackendId.VECTOR to MESSAGE_VECTOR,
        BackendId.PUBSUB to MESSAGE_PUBSUB,
    )

    fun requireSupported(descriptor: HandlerDescriptor) {
        val declared = descriptor.safeRepeat
            ?: throw IllegalStateException(
                "The ${descriptor.backend} handler declares no safe-repeat contract. Stage 1 refuses to start it."
            )
        val supported = SUPPORTED[descriptor.backend]
        if (declared != supported) {
            throw IllegalStateException(
                "The ${descriptor.backend} handler declares ${declared.name} version ${declared.version}. " +
                    "Stage 1 supports ${supported?.name} version ${supported?.version}."
            )
        }
    }
}
