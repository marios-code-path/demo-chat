package com.demo.chat.mcp.config

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** The command line argument that names the configuration file. */
const val CONFIG_ARGUMENT: String = "--config"

/** The environment variable that names the configuration file. */
const val CONFIG_ENVIRONMENT: String = "CHAT_MCP_CONFIG"

/** The largest number of topic ids the adapter accepts. */
const val MAX_TOPIC_IDS: Int = 100

/** Every key the configuration file may hold. An unknown key fails the start. */
val KNOWN_CONFIG_KEYS: Set<String> =
    setOf(
        "backendBaseUrl",
        "credentialFile",
        "keyType",
        "topicIds",
        "enableSend",
        "enableSearch",
    )

/**
 * Read the configuration file path from the process arguments.
 *
 * The adapter accepts [CONFIG_ARGUMENT] alone. Any other argument fails the
 * start, because a misplaced argument must not change behaviour in silence.
 * The environment variable is the fallback.
 */
fun configPathFrom(
    args: List<String>,
    environment: Map<String, String>,
): Path {
    var given: String? = null
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        when {
            argument == CONFIG_ARGUMENT -> {
                if (given != null) throw ConfigException("$CONFIG_ARGUMENT is given twice")
                given = args.getOrNull(index + 1) ?: throw ConfigException("$CONFIG_ARGUMENT needs a path")
                index += 2
            }
            argument.startsWith("$CONFIG_ARGUMENT=") -> {
                if (given != null) throw ConfigException("$CONFIG_ARGUMENT is given twice")
                given = argument.removePrefix("$CONFIG_ARGUMENT=")
                index += 1
            }
            else -> throw ConfigException("unknown argument '$argument'. The adapter takes $CONFIG_ARGUMENT alone")
        }
    }
    val path = given?.takeIf { it.isNotBlank() } ?: environment[CONFIG_ENVIRONMENT]
    if (path.isNullOrBlank()) {
        throw ConfigException("$CONFIG_ARGUMENT or $CONFIG_ENVIRONMENT is required")
    }
    return Path.of(path)
}

/** Read the configuration file and validate every value. */
fun loadConfigFile(path: Path): AdapterConfig {
    if (!Files.isRegularFile(path)) {
        throw ConfigException("the configuration file does not exist: $path")
    }
    val properties = Properties()
    Files.newBufferedReader(path).use { properties.load(it) }
    // A relative credential path resolves against the directory of the
    // configuration file. The process working directory is the client's, so it
    // must not decide which file holds the credential.
    return loadConfig(properties, path.toAbsolutePath().normalize().parent)
}

/** Validate the configuration values. */
fun loadConfig(
    properties: Properties,
    baseDirectory: Path,
): AdapterConfig {
    rejectUnknownKeys(properties)
    val keyType = KeyType.of(properties.getProperty("keyType"))
    return AdapterConfig(
        backendBaseUrl = requireBackendOrigin(required(properties, "backendBaseUrl")),
        credentialFile = baseDirectory.resolve(required(properties, "credentialFile")),
        keyType = keyType,
        topicIds = requireTopicIds(properties.getProperty("topicIds"), keyType),
        enableSend = optionalBoolean(properties, "enableSend"),
        enableSearch = optionalBoolean(properties, "enableSearch"),
    )
}

/**
 * Read the credential from the configured file.
 *
 * The token never arrives as an argument. The whole file is the token, with
 * the surrounding whitespace removed. The value is returned to the caller and
 * it is never stored on the configuration.
 */
fun readCredentialFile(path: Path): String {
    if (!Files.isRegularFile(path)) {
        throw ConfigException("the credential file does not exist: $path")
    }
    val text =
        runCatching { Files.readString(path) }.getOrElse {
            throw ConfigException("the credential file could not be read: $path")
        }
    val token = text.trim()
    if (token.isEmpty()) {
        throw ConfigException("the credential file holds no token: $path")
    }
    return token
}

/**
 * Require a fixed origin.
 *
 * The value must carry a scheme, a host and no other part. A path, a query, a
 * fragment and user info are refused, because a prefix that holds a path
 * invites a later caller to leave the configured prefix.
 */
private fun requireBackendOrigin(text: String): URI {
    val uri =
        runCatching { URI(text) }.getOrNull()
            ?: throw ConfigException("backendBaseUrl is not a URL: '$text'")
    val scheme = uri.scheme?.lowercase() ?: throw ConfigException("backendBaseUrl needs a scheme")
    if (scheme != "http" && scheme != "https") {
        throw ConfigException("backendBaseUrl scheme must be https, or http on a loopback host")
    }
    if (uri.userInfo != null) throw ConfigException("backendBaseUrl must carry no user info")
    if (uri.query != null) throw ConfigException("backendBaseUrl must carry no query")
    if (uri.fragment != null) throw ConfigException("backendBaseUrl must carry no fragment")
    val path = uri.path.orEmpty()
    if (path.isNotEmpty() && path != "/") throw ConfigException("backendBaseUrl must carry no path")
    val host = uri.host ?: throw ConfigException("backendBaseUrl needs a host")
    if (scheme == "http" && !isLoopbackHost(host)) {
        throw ConfigException("backendBaseUrl may use http only on a loopback host, not '$host'")
    }
    return URI(scheme, null, host, uri.port, null, null, null)
}

/** True for a loopback host name. */
private fun isLoopbackHost(host: String): Boolean {
    val name = host.removePrefix("[").removeSuffix("]")
    return name == "localhost" || name == "127.0.0.1" || name == "::1"
}

/** The origin text of a URL: the scheme, the host and the effective port. */
fun originOf(uri: URI): String {
    val scheme = uri.scheme?.lowercase() ?: return ""
    val host = uri.host?.lowercase() ?: return ""
    val port = if (uri.port != -1) uri.port else defaultPortFor(scheme)
    return "$scheme://$host:$port"
}

private fun defaultPortFor(scheme: String): Int =
    when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

/**
 * Refuse a URL whose origin differs from the configured origin.
 *
 * A redirect may not carry the credential to another origin. The client calls
 * this rule for every hop, against the configured base URL.
 */
fun requireSameOrigin(
    configuredBaseUrl: URI,
    target: URI,
) {
    val configured = originOf(configuredBaseUrl)
    val reached = originOf(target)
    if (configured.isEmpty() || reached.isEmpty()) {
        throw ConfigException("a redirect target must carry a scheme and a host")
    }
    if (configured != reached) {
        throw ConfigException("a redirect target must share the origin of backendBaseUrl")
    }
}

/** Refuse a key that the adapter does not know. */
private fun rejectUnknownKeys(properties: Properties) {
    val unknown = properties.stringPropertyNames().filterNot { it in KNOWN_CONFIG_KEYS }.sorted()
    if (unknown.isNotEmpty()) {
        throw ConfigException(
            "the configuration names unknown keys: ${unknown.joinToString(", ")}. " +
                "Known keys: ${KNOWN_CONFIG_KEYS.sorted().joinToString(", ")}",
        )
    }
}

private fun required(
    properties: Properties,
    key: String,
): String =
    properties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: throw ConfigException("$key is required")

/** Read a boolean that defaults to false. */
private fun optionalBoolean(
    properties: Properties,
    key: String,
): Boolean =
    when (val text = properties.getProperty(key)?.trim() ?: return false) {
        "true" -> true
        "false" -> false
        else -> throw ConfigException("$key must be true or false, not '$text'")
    }

/**
 * Require one to [MAX_TOPIC_IDS] unique canonical topic ids.
 *
 * The list is comma separated. Whitespace around a comma is a separator and is
 * removed. Whitespace inside an id is not canonical, so it is refused.
 */
private fun requireTopicIds(
    text: String?,
    keyType: KeyType,
): List<AdapterId> {
    val parts = text.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) {
        throw ConfigException("topicIds needs at least one id")
    }
    if (parts.size > MAX_TOPIC_IDS) {
        throw ConfigException("topicIds holds ${parts.size} ids, above the limit of $MAX_TOPIC_IDS")
    }
    val ids = parts.map { parseIdText(it, keyType) }
    val repeated =
        ids
            .groupingBy { it.text }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
    if (repeated.isNotEmpty()) {
        throw ConfigException("topicIds repeats ${repeated.joinToString(", ")}")
    }
    return ids.toList()
}