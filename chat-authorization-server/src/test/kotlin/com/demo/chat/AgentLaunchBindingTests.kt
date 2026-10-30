package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClientProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.io.File

/**
 * The agent mapping that a deployment supplies at launch. See `CHAT-vidnpywh`.
 *
 * The image build writes each agent property into the image environment as a
 * JVM system property. A launch value must beat that baked value, and a launch
 * list must replace a baked list. These cases measure both results against
 * [AgentClientProperties] itself.
 *
 * `docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/` holds the same
 * measurements against a probe class. This class repeats them against the
 * production type, which is the check that the proof could not make.
 *
 * This class adds no production code, so no case starts red. A failure here
 * means the launch design is wrong.
 */
class AgentLaunchBindingTests {

    private val goldenJson: String =
        repo("shell-scripts/golden/authserv-agent-json.json").readText()

    private val bakedPair = mapOf(
        "app.oauth2.agents[0].client-id" to "baked-one",
        "app.oauth2.agents[0].username" to "BakedOne",
        "app.oauth2.agents[1].client-id" to "baked-two",
        "app.oauth2.agents[1].username" to "BakedTwo",
        "app.oauth2.agent-scope" to "chat.mcp",
    )

    /**
     * Binds `app.oauth2` from one launch value over [baked].
     *
     * The baked flags take the `systemProperties` source, which is where a
     * baked `-D` flag lands at run time. `SpringApplicationJsonEnvironmentPostProcessor`
     * adds its source before that one, so the launch value ranks above the
     * baked value exactly as it does in a container.
     *
     * The real `systemEnvironment` source is removed, so the value that the
     * test runner holds cannot reach the result.
     */
    private fun bind(launchJson: String?, baked: Map<String, Any> = emptyMap()): AgentClientProperties {
        val environment = StandardEnvironment()
        val sources = environment.propertySources
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        sources.replace(
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, baked),
        )
        if (launchJson != null) {
            sources.addLast(MapPropertySource("launch", mapOf("SPRING_APPLICATION_JSON" to launchJson)))
        }
        // The post processor takes a non-null application and does not read it here.
        SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(environment, SpringApplication())
        return Binder(ConfigurationPropertySources.from(sources))
            .bind("app.oauth2", AgentClientProperties::class.java)
            .orElseGet { AgentClientProperties() }
    }

    private fun repo(path: String): File =
        File(System.getProperty("user.dir")).resolveSibling(path)
            .also { check(it.isFile) { "No file at $it" } }

    @Test
    fun `the emitted golden binds two agents and keeps the first JWKS URI`() {
        val bound = bind(goldenJson)

        assertThat(bound.agents.map { it.clientId }).containsExactly("a", "b")
        assertThat(bound.agents.map { it.username }).containsExactly("Agent", "Claude")
        assertThat(bound.agents[0].jwkSetUri).isEqualTo("https://example.test/a/jwks")
        assertThat(bound.agents[1].jwkSetUri).isNull()
    }

    @Test
    fun `the emitted golden binds the agent scope`() {
        assertThat(bind(goldenJson).agentScope).isEqualTo("chat.mcp")
    }

    @Test
    fun `the emitted golden passes the property validation`() {
        val bound = bind(goldenJson)

        assertThat(bound.requireValid()).hasSize(2)
    }

    /**
     * The case that the probe could not prove. A launch list replaces a baked
     * list, so an image that holds two old agents adds nothing.
     */
    @Test
    fun `one launch agent replaces two baked agents`() {
        val launch = """
            {"app":{"oauth2":{"agent-scope":"chat.mcp","agents":[
              {"client-id":"launch-one","username":"LaunchOne",
               "jwk-set-uri":"https://example.test/launch/jwks"}]}}}
        """.trimIndent()

        val bound = bind(launch, bakedPair)

        assertThat(bound.agents).hasSize(1)
        assertThat(bound.agents[0].clientId).isEqualTo("launch-one")
        assertThat(bound.agents[0].jwkSetUri).isEqualTo("https://example.test/launch/jwks")
    }

    @Test
    fun `no baked client id reaches the bound list`() {
        val launch =
            """{"app":{"oauth2":{"agents":[{"client-id":"launch-one","username":"LaunchOne"}]}}}"""

        val bound = bind(launch, bakedPair)

        assertThat(bound.agents.map { it.clientId }).noneMatch { it.startsWith("baked-") }
        assertThat(bound.agents.map { it.username }).noneMatch { it.startsWith("Baked") }
    }

    /**
     * The deployed control. Compose renders an unset operator variable as an
     * empty value, so an empty value must change nothing.
     */
    @Test
    fun `an empty launch value keeps both baked agents`() {
        val bound = bind("", bakedPair)

        assertThat(bound.agents.map { it.clientId }).containsExactly("baked-one", "baked-two")
    }

    @Test
    fun `no launch value keeps both baked agents`() {
        val bound = bind(null, bakedPair)

        assertThat(bound.agents.map { it.clientId }).containsExactly("baked-one", "baked-two")
    }

    @Test
    fun `a launch scope beats a baked scope`() {
        val launch = """{"app":{"oauth2":{"agent-scope":"chat.launch"}}}"""

        assertThat(bind(launch, bakedPair).agentScope).isEqualTo("chat.launch")
    }
}
