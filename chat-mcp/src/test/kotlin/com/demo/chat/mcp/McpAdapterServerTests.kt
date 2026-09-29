package com.demo.chat.mcp

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.AdapterId
import com.demo.chat.mcp.tool.GET_TOPIC_TOOL_NAME
import com.demo.chat.mcp.tool.LIST_TOPICS_TOOL_NAME
import com.demo.chat.mcp.tool.TOPIC_ID_ARGUMENT
import com.demo.chat.mcp.tool.testConfig
import io.modelcontextprotocol.kotlin.sdk.server.Server
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpAdapterServerTests {
    private fun config(): AdapterConfig =
        testConfig(origin = URI("http://127.0.0.1:1"), topicIds = emptyList<AdapterId>())

    private fun server(): Server = createMcpServer(config())

    @Test
    fun `the factory builds a server`() {
        val server: Server = server()
        assertNotNull(server)
    }

    /** Task 4 owns the first two tools. No other tool belongs here. */
    @Test
    fun `the server registers the two topic tools`() {
        assertEquals(setOf(LIST_TOPICS_TOOL_NAME, GET_TOPIC_TOOL_NAME), server().tools.keys)
    }

    @Test
    fun `both tools declare read only and idempotent`() {
        server().tools.forEach { (name, registered) ->
            val annotations = registered.tool.annotations
            assertNotNull(annotations, "the tool $name declares no annotation")
            assertTrue(annotations!!.readOnlyHint == true, "the tool $name is not read only")
            assertTrue(annotations.idempotentHint == true, "the tool $name is not idempotent")
            assertFalse(annotations.destructiveHint == true, "the tool $name declares itself destructive")
        }
    }

    @Test
    fun `both tools declare an input and an output schema`() {
        server().tools.forEach { (name, registered) ->
            val tool = registered.tool
            assertEquals("object", tool.inputSchema.type, "the tool $name declares no object input")
            assertEquals("object", tool.outputSchema?.type, "the tool $name declares no object output")
        }
    }

    @Test
    fun `the list tool declares no input property`() {
        val tool = server().tools.getValue(LIST_TOPICS_TOOL_NAME).tool
        assertTrue(tool.inputSchema.properties!!.isEmpty())
        assertTrue(tool.inputSchema.required!!.isEmpty())
        assertTrue(tool.outputSchema!!.properties!!.containsKey("topics"))
    }

    @Test
    fun `the single read declares its one required input property`() {
        val tool = server().tools.getValue(GET_TOPIC_TOOL_NAME).tool
        assertEquals(setOf(TOPIC_ID_ARGUMENT), tool.inputSchema.properties!!.keys)
        assertEquals(listOf(TOPIC_ID_ARGUMENT), tool.inputSchema.required)
        assertEquals("string", tool.inputSchema.properties!!.getValue(TOPIC_ID_ARGUMENT).at("type"))
        assertTrue(tool.outputSchema!!.properties!!.containsKey("topic"))
    }

    /** Every id is a JSON string, so a Long id keeps every digit. */
    @Test
    fun `the topic schema declares every id as a string`() {
        val tool = server().tools.getValue(GET_TOPIC_TOOL_NAME).tool
        val topic = tool.outputSchema!!.properties!!.getValue("topic")

        assertEquals("string", topic.at("properties", "id", "type"))
        assertEquals("string", topic.at("properties", "root", "type"))
        assertEquals("string", topic.at("properties", "name", "type"))
        assertEquals("false", topic.at("additionalProperties"))
    }

    @Test
    fun `the server identity is the declared name and version`() {
        assertEquals("demo-chat-mcp", MCP_SERVER_NAME)
        assertEquals("0.0.1", MCP_SERVER_VERSION)
    }
}

/** Read one JSON value at a path, as its literal text. */
private fun JsonElement.at(vararg path: String): String {
    var element: JsonElement = this
    for (step in path) {
        element = element.jsonObject.getValue(step)
    }
    return (element as JsonPrimitive).content
}
