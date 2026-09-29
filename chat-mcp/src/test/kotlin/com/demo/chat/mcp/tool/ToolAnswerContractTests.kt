package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The answer shape of one tool call, driven with no server and no transport.
 *
 * Every case here reads the handler itself, so a rule about the error contract
 * fails at the handler and not at a client.
 */
class ToolAnswerContractTests {
    private fun request(arguments: JsonObject? = null): CallToolRequest =
        CallToolRequest(CallToolRequestParams(name = "chat_list_topics", arguments = arguments))

    private fun codeOf(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult): String =
        (result.meta?.get("code") as JsonPrimitive).content

    private fun textOf(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult): String =
        (result.content.single() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text

    /** A good answer carries no application error data. */
    @Test
    fun `a successful call carries no error data`() =
        runBlocking {
            val result = answer("chat_list_topics", request(), emptySet()) { JsonObject(emptyMap()) }

            assertFalse(result.isError == true)
            assertNull(result.meta, "a successful answer carries application error data")
        }

    /** An application failure carries the three fields and no payload. */
    @Test
    fun `a backend failure carries the code and the retryable flag`() =
        runBlocking {
            val result =
                answer("chat_get_topic", request(), emptySet()) {
                    throw ClientException("the backend answered 500", FailureReason.BACKEND, 500)
                }

            assertTrue(result.isError == true)
            assertEquals("BACKEND_UNAVAILABLE", codeOf(result))
            assertEquals(setOf("code", "message", "retryable"), result.meta!!.keys)
            assertNull(result.structuredContent, "a failed answer carries structured content")
        }

    /**
     * A backend exception with sensitive text reaches the client in no form.
     *
     * A transport builds the message of a `ClientException` from the material it
     * handled, so it can hold a URL, a header, a stored value or a credential
     * fragment. The content and `_meta.message` are shaped here, so the guard
     * belongs here. Task 7 rule 4 requires it.
     */
    @Test
    fun `a backend failure exposes no backend exception text`() =
        runBlocking {
            val secret = "sensitive-credential-and-body"
            val result =
                answer("chat_get_topic", request(), emptySet()) {
                    throw ClientException("the backend said $secret", FailureReason.BACKEND, 500)
                }

            assertEquals("BACKEND_UNAVAILABLE", codeOf(result))
            val text = textOf(result)
            assertFalse(text.contains(secret), "the content carries the exception text: $text")
            assertFalse(
                result.meta.toString().contains(secret),
                "the application data carries the exception text: ${result.meta}",
            )
            assertFalse(text.contains("500"), "the content names the backend status: $text")
        }

    /** A refused credential is its own code. */
    @Test
    fun `a refused credential carries the authentication code`() =
        runBlocking {
            val result =
                answer("chat_list_topics", request(), emptySet()) {
                    throw ClientException("the credential is refused", FailureReason.AUTHENTICATION, 401)
                }

            assertEquals("AUTHENTICATION_REQUIRED", codeOf(result))
        }

    /** A size or work limit is its own code, and a repeat does not clear it. */
    @Test
    fun `a passed limit carries the limit code`() =
        runBlocking {
            val result =
                answer("chat_list_topics", request(), emptySet()) {
                    throw ClientException("the adapter holds 4 backend requests already", FailureReason.LIMIT)
                }

            assertEquals("LIMIT_EXCEEDED", codeOf(result))
            assertEquals("false", (result.meta?.get("retryable") as JsonPrimitive).content)
        }

    /**
     * A failed transport answers a repeatable code.
     *
     * The connection never opened, so the read did not run and a repeat may
     * succeed.
     */
    @Test
    fun `a transport failure is retryable`() =
        runBlocking {
            val result =
                answer("chat_list_topics", request(), emptySet()) {
                    throw ClientException("the backend call failed", FailureReason.TRANSPORT)
                }

            assertEquals("BACKEND_UNAVAILABLE", codeOf(result))
            assertEquals("true", (result.meta?.get("retryable") as JsonPrimitive).content)
        }

    /**
     * An unplanned failure answers a fixed sentence.
     *
     * The class name and the message of the caught exception must not reach the
     * client. Both are named in the thrown value, so a leak would find them.
     */
    @Test
    fun `an unplanned failure exposes no exception text`() =
        runBlocking {
            val result =
                answer("chat_list_topics", request(), emptySet()) {
                    throw IllegalStateException("leaked-class-name and leaked-message")
                }

            assertTrue(result.isError == true)
            assertEquals("BACKEND_UNAVAILABLE", codeOf(result))
            val text = textOf(result)
            assertFalse(text.contains("IllegalStateException"), "the answer names the exception class: $text")
            assertFalse(text.contains("leaked-message"), "the answer carries the exception message: $text")
        }

    /**
     * A refusal of the adapter's own keeps its sentence.
     *
     * The sentence tells the caller what to change, and it is built here.
     */
    @Test
    fun `a refusal keeps the adapter sentence`() =
        runBlocking {
            val result =
                answer("chat_get_topic", request(), setOf("topicId")) {
                    throw ToolException("the tool requires an argument named 'topicId'")
                }

            assertEquals("NOT_AVAILABLE", codeOf(result))
            assertTrue(textOf(result).contains("requires an argument named 'topicId'"))
        }

    /**
     * A cancellation reaches the caller and is not answered.
     *
     * The design requires cancellation to reach an outstanding read. A
     * `CancellationException` is a kind of `Exception`, so a handler without
     * this rule would answer a cancelled call as a backend failure.
     */
    @Test
    fun `a cancellation propagates and is not answered`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                answer("chat_list_topics", request(), emptySet()) {
                    throw CancellationException("the caller cancelled the call")
                }
            }
        }
    }

    /**
     * An unknown argument is refused before the body runs.
     *
     * The tool schema cannot state `additionalProperties`, so this check is the
     * contract.
     */
    @Test
    fun `an unknown argument is refused before the body runs`() =
        runBlocking {
            var ran = false
            val arguments = JsonObject(mapOf("extra" to JsonPrimitive("value")))

            val result =
                answer("chat_list_topics", request(arguments), emptySet()) {
                    ran = true
                    JsonObject(emptyMap())
                }

            assertFalse(ran, "the body ran for a refused argument")
            assertEquals("NOT_AVAILABLE", codeOf(result))
            assertTrue(textOf(result).contains("no argument named 'extra'"))
        }
}
