package com.demo.chat.pubsub.memory.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.NoEffectRefusal
import reactor.core.publisher.Sinks
import java.io.IOException

/** An emission that a retry can complete. It is an `IOException`, so the policy treats it as transient. */
class PublicationRetryableException(topic: Any?, val result: Sinks.EmitResult) :
    IOException("The emission to room $topic returned $result. A retry can succeed.")

/** An emission that a retry cannot complete. The sink refused it, so no effect happened. */
class PublicationRefusedException(topic: Any?, val result: Sinks.EmitResult) :
    ChatException("The emission to room $topic returned $result."), NoEffectRefusal
