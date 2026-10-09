package com.demo.chat.mcp.client

class SubmissionUnknownException(
    val requestId: String,
    val status: Int? = null,
) : RuntimeException("the submission outcome is unknown")
