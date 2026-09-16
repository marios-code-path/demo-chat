package com.demo.chat.service.vector

/**
 * The answer of one rebuild trigger.
 *
 * [accepted] states whether this call started a run. A rejected trigger means
 * another run holds the claim, and it creates no job.
 *
 * The field exists because the status cannot carry that fact. A rejected
 * trigger and an accepted one both report running=true, so a reader that saw
 * the status alone waited for a job that no call had created, and then reported
 * a missing durable record. See CHAT-cxduiwjj.
 *
 * The read operation answers with [VectorIndexStatus] and not with this type.
 * A read never asks whether it started anything.
 */
data class VectorIndexTriggerResult<T>(
    val accepted: Boolean,
    val status: VectorIndexStatus<T>,
)
