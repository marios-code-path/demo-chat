package com.demo.chat.mcp.client

/**
 * A backend call that the adapter refuses, or cannot complete.
 *
 * The message names the rule and the failing field. It carries no credential
 * and no message text.
 */
class ClientException(message: String) : RuntimeException(message)
