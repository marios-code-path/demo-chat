package com.demo.chat.mcp.config

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Where the portal socket path comes from.
 *
 * A fixed path is read once. A file-backed path is read again for each call,
 * so a portal restart under a new path needs no adapter restart.
 */
sealed interface PortalSocketSource {
    fun resolve(): Path

    data class Fixed(val path: Path) : PortalSocketSource {
        override fun resolve(): Path = path
    }

    data class FileBacked(val file: Path) : PortalSocketSource {
        override fun resolve(): Path {
            if (!Files.isRegularFile(file)) {
                throw ConfigException("the socket file does not exist: $file")
            }
            val text = try {
                Files.readString(file)
            } catch (failure: Exception) {
                throw ConfigException("cannot read the socket file $file")
            }
            val first = text.substringBefore('\n').removeSuffix("\r")
            if (first.isEmpty()) throw ConfigException("the socket file is empty: $file")
            val path = try {
                Path.of(first)
            } catch (failure: InvalidPathException) {
                throw ConfigException("the socket file holds a path that is not valid: '$first'")
            }
            if (!path.isAbsolute) throw ConfigException("the socket file must hold an absolute path: '$first'")
            return path
        }
    }
}

/** The settings that select the tools of one adapter process. */
sealed interface McpLaunchConfig {
    /** Serve the HTTP tools and any configured portal tool. */
    data class Http(val adapter: AdapterConfig) : McpLaunchConfig

    /** Serve one register tool through one local portal. */
    data class PortalOnly(
        val portalSocketSource: PortalSocketSource,
        val registerSelector: String,
        /** The register name. An id gives no topic prefix, so the write tools need it. */
        val registerName: String? = null,
    ) : McpLaunchConfig
}
