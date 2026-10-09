package com.demo.chat.mcp.error

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The error contract of one application failure.
 *
 * The code set is closed. A client reads the code and not a sentence.
 */
class ToolErrorTests {
    @Test
    fun `messaging errors use the approved fixed sentences`() {
        val expected = mapOf(
            ToolErrorCode.INVALID_INPUT to "the tool input is not valid",
            ToolErrorCode.REQUEST_CONFLICT to "the request ID conflicts with an earlier submission",
            ToolErrorCode.COMMAND_INCOMPLETE to "a required backend refused the command",
            ToolErrorCode.OUTCOME_UNKNOWN to "the submission outcome is unknown. " +
                "Repeat only with the same request ID while the server process remains unchanged.",
        )
        expected.forEach { (code, message) ->
            assertEquals(message, ToolError.fixed(code).message)
            assertFalse(ToolError.fixed(code).retryable)
        }
    }

    /** One reason, one code. The table is complete over [FailureReason]. */
    @Test
    fun `every failure class maps to its code`() {
        val expected =
            mapOf(
                FailureReason.AUTHENTICATION to ToolErrorCode.AUTHENTICATION_REQUIRED,
                FailureReason.NOT_AVAILABLE to ToolErrorCode.NOT_AVAILABLE,
                FailureReason.BACKEND to ToolErrorCode.BACKEND_UNAVAILABLE,
                FailureReason.TRANSPORT to ToolErrorCode.BACKEND_UNAVAILABLE,
                FailureReason.LIMIT to ToolErrorCode.LIMIT_EXCEEDED,
                FailureReason.PROTOCOL to ToolErrorCode.BACKEND_UNAVAILABLE,
            )

        assertEquals(
            FailureReason.entries.toSet(),
            expected.keys,
            "the table does not cover every failure class, so a new class would map to nothing",
        )
        expected.forEach { (reason, code) ->
            assertEquals(code, ToolError.codeOf(reason), "the class $reason maps to the wrong code")
        }
    }

    /**
     * A transport failure is the one class a repeat may clear.
     *
     * Every other class is decided by the request or by the stored data, so a
     * repeat gives the same answer.
     */
    @Test
    fun `only a transport failure is retryable`() {
        FailureReason.entries.forEach { reason ->
            val error = ToolError.of(ClientException("a sentence", reason))
            assertEquals(
                reason == FailureReason.TRANSPORT,
                error.retryable,
                "the class $reason reports the wrong retryable flag",
            )
        }
    }

    /** The three application fields sit flat, as the design names them. */
    @Test
    fun `the application data carries three flat fields`() {
        val meta = ToolError(ToolErrorCode.LIMIT_EXCEEDED, "a sentence", true).toMeta()

        assertEquals(setOf("code", "message", "retryable"), meta.keys)
        assertEquals("LIMIT_EXCEEDED", (meta.getValue("code") as JsonPrimitive).content)
        assertEquals("a sentence", (meta.getValue("message") as JsonPrimitive).content)
        assertEquals("true", (meta.getValue("retryable") as JsonPrimitive).content)
    }

    /** A refusal of the adapter's own has no repeat value. */
    @Test
    fun `a refusal is not retryable`() {
        val error = ToolError.refused("the tool refused the request")

        assertEquals(ToolErrorCode.NOT_AVAILABLE, error.code)
        assertFalse(error.retryable)
    }

    /** An unplanned failure names the backend and offers no repeat. */
    @Test
    fun `an internal failure is not retryable`() {
        val error = ToolError.internalFailure()

        assertEquals(ToolErrorCode.BACKEND_UNAVAILABLE, error.code)
        assertFalse(error.retryable)
        assertTrue(error.message.isNotBlank())
    }

    /**
     * A denied object and an absent object answer one error.
     *
     * The two backend statuses differ. The client reads the same code and the
     * same sentence, so it cannot tell a hidden object from an absent one.
     */
    @Test
    fun `a denied object and an absent object answer the same error`() {
        val denied = ToolError.of(ClientException("the backend answered 403", FailureReason.NOT_AVAILABLE, 403))
        val absent = ToolError.of(ClientException("the backend answered 404", FailureReason.NOT_AVAILABLE, 404))

        assertEquals(absent, denied)
    }

    /** The fixed sentence names no status, because a status would separate the two. */
    @Test
    fun `the not available sentence names no backend status`() {
        val error = ToolError.of(ClientException("the backend answered 404", FailureReason.NOT_AVAILABLE, 404))

        assertEquals(ToolError.NOT_AVAILABLE_MESSAGE, error.message)
        assertFalse(error.message.contains("404"), "the sentence names the backend status")
    }

    /**
     * Every code answers one fixed sentence, and the six sentences differ.
     *
     * Two codes with one sentence would tell a client nothing. A blank sentence
     * would tell it less. The `when` in `messageOf` has no `else`, so a new code
     * fails the compile before it reaches this test.
     */
    @Test
    fun `every failure code answers its own fixed sentence`() {
        val sentences = ToolErrorCode.entries.map { ToolError.messageOf(it) }

        sentences.forEachIndexed { index, sentence ->
            assertTrue(sentence.isNotBlank(), "the code ${ToolErrorCode.entries[index]} has no sentence")
        }
        assertEquals(
            ToolErrorCode.entries.size,
            sentences.toSet().size,
            "two codes share one sentence, so a client cannot tell them apart",
        )
    }

    /**
     * No failure class repeats the text of the backend exception.
     *
     * A `ClientException` message is built by a transport from the material it
     * handled. It can hold a URL, a header, a stored value or a credential
     * fragment. **This is the regression guard for task 7 rule 4**, and the
     * token below stands in for that text. Every failure class is exercised, so
     * a new class cannot take a different path.
     */
    @Test
    fun `no failure class repeats the backend exception text`() {
        FailureReason.entries.forEach { reason ->
            val secret = "sensitive-$reason-token"
            val error = ToolError.of(ClientException("the backend said $secret", reason, 500))

            assertFalse(
                error.message.contains(secret),
                "the class $reason repeats the backend exception text in its message",
            )
            assertFalse(
                error.toMeta().toString().contains(secret),
                "the class $reason repeats the backend exception text in its application data",
            )
            assertFalse(
                error.message.contains("ClientException"),
                "the class $reason names the exception type",
            )
        }
    }

    /**
     * The sentence of a failure does not depend on the failure.
     *
     * Two exceptions with different text, one reason and one status answer one
     * value. A reader of `of` sees no path from the exception text to the
     * answer, and this pins that.
     */
    @Test
    fun `the sentence of a failure does not depend on the exception text`() {
        FailureReason.entries.forEach { reason ->
            val first = ToolError.of(ClientException("the first sentence", reason, 500))
            val second = ToolError.of(ClientException("the second sentence", reason, 500))

            assertEquals(first, second, "the class $reason answers different values for one failure class")
            assertEquals(ToolError.messageOf(first.code), first.message)
        }
    }
}
