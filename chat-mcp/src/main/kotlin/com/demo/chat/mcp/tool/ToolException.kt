package com.demo.chat.mcp.tool

/**
 * A tool argument that the adapter refuses.
 *
 * The adapter raises this before any backend call. The message names the rule.
 * It carries no credential and no backend text.
 */
class ToolException(message: String) : RuntimeException(message)
