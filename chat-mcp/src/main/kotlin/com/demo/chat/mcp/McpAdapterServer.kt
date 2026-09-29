package com.demo.chat.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/** The name a client reads during discovery. It is stable within this API version. */
const val MCP_SERVER_NAME: String = "demo-chat-mcp"

/** The version a client reads during discovery. It follows this artifact version. */
const val MCP_SERVER_VERSION: String = "0.0.1"

/**
 * Build the MCP server.
 *
 * The server declares one capability, tools. It declares no prompt, no
 * resource and no subscription. See
 * `docs/superpowers/specs/2026-09-27-demo-chat-mcp-design.md` section 4.
 *
 * No tool is registered here. A later task registers the first two tools.
 */
fun createMcpServer(): Server =
    Server(
        Implementation(name = MCP_SERVER_NAME, version = MCP_SERVER_VERSION),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )
