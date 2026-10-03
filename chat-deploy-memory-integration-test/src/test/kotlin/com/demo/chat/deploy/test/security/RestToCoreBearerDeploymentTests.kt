package com.demo.chat.deploy.test.security

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.User
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import tools.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/** Proves the bearer identity across separate core and REST JVM processes. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "run.rest.core.e2e", matches = "true")
class RestToCoreBearerDeploymentTests {

    private lateinit var core: Process
    private lateinit var rest: Process
    private lateinit var coreLog: Path
    private lateinit var restLog: Path

    private val root: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isExecutable(it.resolve("shell-scripts/chat-build")) }
    private val javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
    private val coreJar = root.resolve("chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar")
    private val restJar = root.resolve("chat-deploy/target/chat-deploy-0.0.1-exec.jar")
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private val corePort = 17090
    private val restPort = corePort + 2

    @BeforeAll
    fun startDeployments() {
        val key = DeployTestSigningKey.path()
        core = start(
            "core", "--memory", "--run", "--notls", "--node-id", "1",
            "--init", "users,rootkeys", "--jwk", key,
            "--agent-client-id", "client-under-test", "--agent-username", "Agent",
            "--agent-scope", "chat.mcp",
        )
        await("http://127.0.0.1:${corePort + 1}/actuator/health", core)

        rest = start(
            "rest", "--run", "--notls", "--node-id", "2", "--jwk", key,
            "--agent-client-id", "client-under-test", "--agent-username", "Agent",
            "--agent-scope", "chat.mcp",
        )
        await("http://127.0.0.1:${restPort + 1}/actuator/health", rest)
    }

    @AfterAll
    fun stopDeployments() {
        if (::core.isInitialized) stop(core)
        if (::rest.isInitialized) stop(rest)
    }

    @Test
    fun `root keys require actuator credentials`() {
        val uri = URI("http://127.0.0.1:${corePort + 1}/actuator/rootkeys")
        val anonymous = request(HttpRequest.newBuilder(uri).GET().build())
        assertThat(anonymous.statusCode()).isEqualTo(401)

        val credentials = java.util.Base64.getEncoder().encodeToString("actuator:actuator".toByteArray())
        val authenticated = request(
            HttpRequest.newBuilder(uri)
                .header("Authorization", "Basic $credentials")
                .GET().build()
        )
        assertThat(authenticated.statusCode()).isEqualTo(200)
    }

    @Test
    fun `a valid REST bearer token reaches core authorization as the configured identity`() {
        val token = DeployTestSigningKey.agentToken()
        val created = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/new"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"ByNameRequest\",\"name\":\"rest-relay-room\"}"))
                .build(),
        )

        assertThat(created.statusCode()).isEqualTo(201)
        val id = Regex("\\\"id\\\"\\s*:\\s*(\\d+)")
            .find(created.body())
            ?.groupValues
            ?.get(1)
        assertThat(id).describedAs("the created room key in the REST response").isNotNull
        assertOwnerIsConfiguredAgent(id!!)

        val removed = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/id/$id"))
                .header("Authorization", "Bearer $token")
                .DELETE()
                .build(),
        )

        assertThat(removed.statusCode()).isEqualTo(204)
    }

    private fun assertOwnerIsConfiguredAgent(roomId: String) {
        val mapper = JsonMapper.builder().addModule(ChatJackson3Modules().chatJackson3Module()).build()
        val requester = RSocketRequester.builder()
            .rsocketStrategies(
                RSocketStrategies.builder()
                    .encoder(SimpleAuthenticationEncoder())
                    .encoder(JacksonJsonEncoder(mapper))
                    .decoder(JacksonJsonDecoder(mapper))
                    .build()
            )
            .setupMetadata(
                UsernamePasswordMetadata("Service", "rest-core-service-secret"),
                MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0"),
            )
            .tcp("127.0.0.1", corePort)
        try {
            val agent = requester.route("user.user-by-handle")
                .data(ByStringRequest("Agent"))
                .retrieveFlux(User::class.java).single().block(Duration.ofSeconds(10))!!
            val grants = requester.route("persist.authmetadata.all")
                .retrieveFlux(AuthMetadata::class.java).collectList().block(Duration.ofSeconds(10))!!
            val owners = grants.filter {
                it.target.id.toString() == roomId && it.permission == "*" && !it.mute
            }
            assertThat(owners).describedAs("the room owner grant").hasSize(1)
            assertThat(owners.single().principal)
                .describedAs("the identity recorded by core authorization")
                .isEqualTo(agent.key)
        } finally {
            requester.dispose()
        }
    }

    private fun start(service: String, vararg arguments: String): Process {
        val log = Files.createTempFile("rest-core-bearer-$service", ".log").toFile()
        if (service == "core") coreLog = log.toPath() else restLog = log.toPath()
        val jar = if (service == "core") coreJar else restJar
        require(Files.isRegularFile(jar)) { "The reactor-built executable jar is missing: $jar" }
        val flags = if (service == "core") coreFlags(arguments) else restFlags(arguments)
        return ProcessBuilder(*(listOf(javaExecutable) + flags + listOf("-jar", jar.toString())).toTypedArray())
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log)
            .apply {
                environment()["CORE_PORT"] = corePort.toString()
                environment()["CORE_HOST"] = "127.0.0.1"
                environment()["CHAT_SERVICE_PASSWORD"] = "rest-core-service-secret"
                environment()["CHAT_AGENT_PASSWORD"] = "rest-core-agent-secret"
            }
            .start()
    }

    private fun coreFlags(arguments: Array<out String>): List<String> = listOf(
        "-Dspring.profiles.active=prod",
        "-Dmanagement.endpoint.shutdown.enabled=true",
        "-Dmanagement.endpoint.health.enabled=true",
        "-Dmanagement.endpoint.rootkeys.enabled=true",
        "-Dmanagement.endpoints.web.exposure.include=shutdown,health,rootkeys",
        "-Dapp.actuator.username=actuator", "-Dapp.actuator.password=actuator",
        "-Dapp.key.type=long", "-Dapp.nodeid=1", "-Dapp.primary=core-service",
        "-Dspring.application.name=core-service-rsocket",
        "-Dapp.server.proto=rsocket", "-Dspring.main.web-application-type=reactive",
        "-Dserver.port=${corePort + 1}", "-Dmanagement.server.port=${corePort + 1}",
        "-Dspring.rsocket.server.port=$corePort",
        "-Dapp.users.create=true", "-Dspring.security.user.name=actuator",
        "-Dspring.security.user.password=actuator", "-Dspring.security.user.roles=ACTUATOR",
        "-Dspring.cloud.consul.enabled=false",
        "-Dspring.cloud.service-registry.auto-registration.enabled=false",
        "-Dspring.cloud.consul.config.enabled=false",
        "-Dspring.cloud.consul.config.watch.enabled=false",
        "-Dspring.cloud.consul.discovery.enabled=false",
        "-Dapp.service.core.key=memory", "-Dapp.service.core.pubsub=memory",
        "-Dapp.service.core.index=lucene", "-Dapp.service.core.persistence=memory",
        "-Dapp.service.core.secrets=memory", "-Dapp.service.composite=true",
        "-Dapp.service.composite.auth=true",
        "-Dapp.controller.key=true", "-Dapp.controller.persistence=true",
        "-Dapp.controller.index=true", "-Dapp.controller.pubsub=true",
        "-Dapp.controller.secrets=true", "-Dapp.controller.user=true",
        "-Dapp.controller.topic=true", "-Dapp.controller.message=true",
        "-Dapp.service.security.userdetails=true",
        "-Dspring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "-Dapp.security.agent.client-id=client-under-test",
        "-Dapp.security.agent.username=Agent",
        "-Dapp.security.agent.required-scope=chat.mcp",
        "-Dapp.security.jwt.jwk-path=${arguments[arguments.indexOf("--jwk") + 1]}",
    )

    private fun restFlags(arguments: Array<out String>): List<String> = listOf(
        "-Dspring.profiles.active=prod",
        "-Dmanagement.endpoint.shutdown.enabled=true",
        "-Dmanagement.endpoint.health.enabled=true",
        "-Dmanagement.endpoints.web.exposure.include=shutdown,health",
        "-Dapp.key.type=long", "-Dapp.nodeid=2", "-Dapp.primary=REST",
        "-Dspring.application.name=core-service-http",
        "-Dapp.server.proto=http", "-Dserver.port=$restPort", "-Dmanagement.server.port=${restPort + 1}",
        "-Dapp.client.protocol=rsocket", "-Dapp.client.discovery=properties",
        "-Dapp.client.rsocket.composite.user=true", "-Dapp.client.rsocket.composite.topic=true",
        "-Dapp.client.rsocket.composite.message=true",
        "-Dapp.rootkeys.consume.scheme=http",
        "-Dapp.rootkeys.consume.source=http://127.0.0.1:${corePort + 1}",
        "-Dapp.controller.user=true", "-Dapp.controller.topic=true", "-Dapp.controller.message=true",
        "-Dapp.rsocket.transport.security.type=unprotected",
        "-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration",
        "-Dspring.cloud.consul.enabled=false",
        "-Dspring.cloud.service-registry.auto-registration.enabled=false",
        "-Dspring.cloud.consul.config.enabled=false",
        "-Dspring.cloud.consul.config.watch.enabled=false",
        "-Dspring.cloud.consul.discovery.enabled=false",
        "-Dspring.security.user.name=actuator", "-Dspring.security.user.password=actuator",
        "-Dspring.security.user.roles=ACTUATOR",
        "-Dspring.config.additional-location=file:${root.resolve("shared-deploy-configuration/src/main/config/client-rsocket-local.yml")}",
        "-Dapp.security.agent.client-id=client-under-test",
        "-Dapp.security.agent.username=Agent",
        "-Dapp.security.agent.required-scope=chat.mcp",
        "-Dapp.security.jwt.jwk-path=${arguments[arguments.indexOf("--jwk") + 1]}",
    )

    private fun await(url: String, process: Process) {
        repeat(120) {
            if (!process.isAlive) {
                val log = if (process === core) coreLog else restLog
                error("Deployment process exited before readiness.\n${Files.readString(log)}")
            }
            runCatching {
                val response = request(
                    HttpRequest.newBuilder(URI(url)).GET().build()
                )
                if (response.statusCode() == 200) return
            }
            Thread.sleep(250)
        }
        error("Deployment did not become ready: $url")
    }

    private fun request(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun stop(process: Process) {
        process.destroy()
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}
