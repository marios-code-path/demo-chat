package com.demo.chat.mcp.config

import java.nio.file.Path

/** The settings that select the tools of one adapter process. */
sealed interface McpLaunchConfig {
    /** Serve the HTTP tools and any configured portal tool. */
    data class Http(val adapter: AdapterConfig) : McpLaunchConfig

    /** Serve one register tool through one local portal. */
    data class PortalOnly(
        val portalSocket: Path,
        val registerSelector: String,
        /** The register name. An id gives no topic prefix, so the write tools need it. */
        val registerName: String? = null,
    ) : McpLaunchConfig
}
