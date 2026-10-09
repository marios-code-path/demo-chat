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
            "--agent", "client-under-test=Agent", "--agent", "client-claude=Claude",
            "--agent-scope", "chat.mcp",
        )
        await("http://127.0.0.1:${corePort + 1}/actuator/health", core)

        rest = start(
            "rest", "--run", "--notls", "--node-id", "2", "--jwk", key,
            "--agent", "client-under-test=Agent", "--agent", "client-claude=Claude",
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
        assertOwnerIs(id!!, "Agent")

        val removed = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/id/$id"))
                .header("Authorization", "Bearer $token")
                .DELETE()
                .build(),
        )

        assertThat(removed.statusCode()).isEqualTo(204)
    }

    @Test
    fun `each REST agent token reaches core authorization as its own identity`() {
        listOf("client-under-test" to "Agent", "client-claude" to "Claude").forEach { (clientId, handle) ->
            val token = DeployTestSigningKey.mint(clientId, "chat.mcp")
            val created = request(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/new"))
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"ByNameRequest\",\"name\":\"relay${handle.lowercase()}\"}"))
                    .build(),
            )

            assertThat(created.statusCode()).describedAs("the room add of $handle").isEqualTo(201)
            val id = Regex("\\\"id\\\"\\s*:\\s*(\\d+)").find(created.body())!!.groupValues[1]
            assertOwnerIs(id, handle)
        }
    }

    @Test
    fun `a token from an unlisted client answers 401 on REST`() {
        val response = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/list"))
                .header("Authorization", "Bearer ${DeployTestSigningKey.mint("client-unlisted", "chat.mcp")}")
                .GET().build(),
        )

        assertThat(response.statusCode()).isEqualTo(401)
    }

    /**
     * A method security denial at the core must reach REST as 403.
     *
     * The core refuses this call in `@PreAuthorize`, inside the handler. That
     * is after the payload interceptor chain completes. The agent holds no
     * owner row on a room that `Anon` created.
     */
    @Test
    fun `a core method security denial reaches REST as 403`() {
        val anonymous = coreRequester(credential = null)
        val roomId = try {
            val created = anonymous.route("topic.topic-add")
                .data(ByStringRequest("anonroom"))
                .retrieveMono(Map::class.java)
                .block(Duration.ofSeconds(10))
            Regex("id=(\\d+)").find(created.toString())?.groupValues?.get(1)
        } finally {
            anonymous.dispose()
        }
        assertThat(roomId).describedAs("the key of the room that Anon created").isNotNull

        val removed = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/id/$roomId"))
                .header("Authorization", "Bearer ${DeployTestSigningKey.agentToken()}")
                .DELETE()
                .build(),
        )

        assertThat(removed.statusCode())
            .describedAs("the REST status for a core @PreAuthorize denial. Body: ${removed.body()}")
            .isEqualTo(403)
    }

    /**
     * A core miss must reach REST as 404, with the core message. See
     * `CHAT-undefoqd`.
     *
     * The name route holds no access check, and the core answers an unknown
     * name with `NotFoundException`. So the miss leaves the core as code
     * `0x404`, and the client decoder makes `CoreNotFound` from it.
     */
    @Test
    fun `a core miss reaches REST as 404`() {
        val missed = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/name/no-such-room"))
                .header("Authorization", "Bearer ${DeployTestSigningKey.agentToken()}")
                .GET()
                .build(),
        )

        assertThat(missed.statusCode())
            .describedAs("the REST status for a core miss. Body: ${missed.body()}")
            .isEqualTo(404)
        assertThat(missed.body()).describedAs("the core message").isNotBlank
    }

    /**
     * The REST route for `listMessages` reads the stored messages, and the
     * response completes. See `CHAT-evxtlmfs`.
     *
     * The agent owns the room it creates, so it holds `SUBSCRIBE` there. A
     * room that `Anon` created gives the agent no `SUBSCRIBE`, so that read
     * answers 403. An unknown room answers 404.
     *
     * **The room name is one token.** The Lucene name index splits on a
     * hyphen, so `rest-list-room` matched `rest-relay-room` and the second
     * add failed as a duplicate. See `CHAT-hajmhslp`.
     */
    @Test
    fun `the REST message list reads stored messages, refuses a non-subscriber, and misses an unknown room`() {
        val token = DeployTestSigningKey.agentToken()
        val created = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/new"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"ByNameRequest\",\"name\":\"restlistroom\"}"))
                .build(),
        )
        assertThat(created.statusCode()).isEqualTo(201)
        val roomId = Regex("\\\"id\\\"\\s*:\\s*(\\d+)").find(created.body())?.groupValues?.get(1)
        assertThat(roomId).describedAs("the created room key in the REST response").isNotNull

        val sent = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/message/send/$roomId"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("stored-history-line"))
                .build(),
        )
        assertThat(sent.statusCode()).describedAs("the send. Body: ${sent.body()}").isEqualTo(201)

        val listed = client.sendAsync(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/message/list/$roomId"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ).get(10, java.util.concurrent.TimeUnit.SECONDS)
        assertThat(listed.statusCode()).describedAs("the list. Body: ${listed.body()}").isEqualTo(200)
        assertThat(listed.body()).contains("stored-history-line")

        val anonymous = coreRequester(credential = null)
        val anonRoomId = try {
            val anonRoom = anonymous.route("topic.topic-add")
                .data(ByStringRequest("anonlistroom"))
                .retrieveMono(Map::class.java)
                .block(Duration.ofSeconds(10))
            Regex("id=(\\d+)").find(anonRoom.toString())?.groupValues?.get(1)
        } finally {
            anonymous.dispose()
        }
        val refused = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/message/list/$anonRoomId"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
        )
        assertThat(refused.statusCode()).describedAs("the non-subscriber list. Body: ${refused.body()}").isEqualTo(403)

        val unknown = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/message/list/987654321"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
        )
        assertThat(unknown.statusCode()).describedAs("the unknown room list. Body: ${unknown.body()}").isEqualTo(404)
    }

    private fun coreRequester(credential: UsernamePasswordMetadata?): RSocketRequester {
        val mapper = JsonMapper.builder().addModule(ChatJackson3Modules().chatJackson3Module()).build()
        val builder = RSocketRequester.builder()
            .rsocketStrategies(
                RSocketStrategies.builder()
                    .encoder(SimpleAuthenticationEncoder())
                    .encoder(JacksonJsonEncoder(mapper))
                    .decoder(JacksonJsonDecoder(mapper))
                    .build()
            )
        credential?.let {
            builder.setupMetadata(it, MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0"))
        }
        return builder.tcp("127.0.0.1", corePort)
    }

    private fun assertOwnerIs(roomId: String, handle: String) {
        val requester = coreRequester(UsernamePasswordMetadata("Service", "rest-core-service-secret"))
        try {
            val agent = requester.route("user.user-by-handle")
                .data(ByStringRequest(handle))
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
        "-Dapp.service.core.secrets=memory", "-Dapp.service.composite=true", "-Dapp.command.bus=memory",
        "-Dapp.service.composite.auth=true",
        "-Dapp.controller.key=true", "-Dapp.controller.persistence=true",
        "-Dapp.controller.index=true", "-Dapp.controller.pubsub=true",
        "-Dapp.controller.secrets=true", "-Dapp.controller.user=true",
        "-Dapp.controller.topic=true", "-Dapp.controller.message=true",
        "-Dapp.service.security.userdetails=true",
        "-Dspring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "-Dapp.init.initial-users[Claude].handle=Claude",
        "-Dapp.init.initial-users[Claude].name=Claude",
        "-Dapp.init.initial-users[Claude].image-uri=chatimg://agent.png",
        "-Dapp.security.required-scope=chat.mcp",
        "-Dapp.security.agents[0].client-id=client-under-test",
        "-Dapp.security.agents[0].username=Agent",
        "-Dapp.security.agents[1].client-id=client-claude",
        "-Dapp.security.agents[1].username=Claude",
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
        "-Dapp.security.required-scope=chat.mcp",
        "-Dapp.security.agents[0].client-id=client-under-test",
        "-Dapp.security.agents[0].username=Agent",
        "-Dapp.security.agents[1].client-id=client-claude",
        "-Dapp.security.agents[1].username=Claude",
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
