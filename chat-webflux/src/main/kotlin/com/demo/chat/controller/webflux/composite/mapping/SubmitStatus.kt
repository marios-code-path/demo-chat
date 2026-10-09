package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.domain.command.CallerOutcome

/** The REST status of each caller outcome. The body is the authority. Decision 10 of the spec. */
object SubmitStatus {
    fun of(outcome: CallerOutcome): Int = when (outcome) {
        CallerOutcome.COMPLETED -> 201
        CallerOutcome.ACCEPTED, CallerOutcome.PENDING -> 202
        CallerOutcome.INCOMPLETE -> 424
    }
}
