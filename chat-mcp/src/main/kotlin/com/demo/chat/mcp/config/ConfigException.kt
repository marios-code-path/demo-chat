package com.demo.chat.mcp.config

/**
 * A configuration value that the adapter refuses.
 *
 * The message names the key and the rule. It carries no credential.
 */
class ConfigException(message: String) : RuntimeException(message)