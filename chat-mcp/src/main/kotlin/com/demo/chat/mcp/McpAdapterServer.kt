package com.demo.chat.mcp

import com.demo.chat.mcp.client.BackendHttp
import com.demo.chat.mcp.client.TopicClient
import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.tool.TopicToolService
import com.demo.chat.mcp.tool.registerTopicTools
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
 * The two topic tools are registered here. A later task adds no other surface.
 *
 * The transport is required. **The adapter owns one transport for the whole
 * process**, and the caller closes it. A transport built here would belong to
 * this function, and no caller could close it.
 */
fun createMcpServer(config: AdapterConfig, http: BackendHttp): Server {
    val server =
        Server(
            Implementation(name = MCP_SERVER_NAME, version = MCP_SERVER_VERSION),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )
    registerTopicTools(server, TopicToolService(config, TopicClient(config, http)))
    return server
}
