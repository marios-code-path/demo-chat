package com.demo.chat.mcp.config

import java.net.URI
import java.nio.file.Path

/**
 * The adapter configuration.
 *
 * The token is absent on purpose. It is read from [credentialFile] at the
 * moment of use, so no log line and no `toString` can carry it.
 */
data class AdapterConfig(
    /** The fixed backend origin. It holds a scheme, a host and a port alone. */
    val backendBaseUrl: URI,
    /** The file that holds the credential. A relative value resolves against the configuration file. */
    val credentialFile: Path,
    /** The key type of the deployment. */
    val keyType: KeyType,
    /** The topic ids the adapter may read. One to 100, each canonical and unique. */
    val topicIds: List<AdapterId>,
    /** Whether the adapter registers a send tool. The default is false. */
    val enableSend: Boolean,
    /** Whether the adapter registers a search tool. The default is false. */
    val enableSearch: Boolean,
)