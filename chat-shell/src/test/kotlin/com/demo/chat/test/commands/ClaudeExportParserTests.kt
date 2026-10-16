package com.demo.chat.test.commands

import com.demo.chat.shell.commands.ClaudeExportParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class ClaudeExportParserTests {
    private val fixture = Path.of("src/test/resources/import/claude-conversations.json")

    @TempDir
    lateinit var temp: Path

    private fun export(content: String, flatText: String = "hidden flat text"): Path {
        val file = temp.resolve("conversations.json")
        Files.writeString(file, """[
          {"uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"One","chat_messages":[
            {"uuid":"11111111-1111-4111-8111-111111111111","sender":"human",
             "created_at":"2024-01-01T10:00:00Z","text":"$flatText","content":$content}
          ]}
        ]""")
        return file
    }

    /** A fork with a complete path. Both branches end at a present root. */
    private fun forkedExport(rootParent: String? = null): Path {
        val parentField = rootParent?.let { ""","parent_message_uuid":"$it"""" }.orEmpty()
        val file = temp.resolve("forked-conversations.json")
        Files.writeString(file, """[
          {"uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"Forked","chat_messages":[
            {"uuid":"11111111-1111-4111-8111-111111111111","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"Root"$parentField},
            {"uuid":"22222222-2222-4222-8222-222222222222","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"First branch","parent_message_uuid":"11111111-1111-4111-8111-111111111111"},
            {"uuid":"33333333-3333-4333-8333-333333333333","sender":"assistant","created_at":"2024-01-01T10:00:02Z","text":"Second branch","parent_message_uuid":"11111111-1111-4111-8111-111111111111"}
          ]}
        ]""")
        return file
    }

    @Test
    fun `content text blocks join and flat text handles empty content`() {
        val parsed = ClaudeExportParser().parse(fixture, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

        assertThat(parsed.threads).hasSize(1)
        assertThat(parsed.threads.single().messages.map { it.text }).containsExactly("First turn.", "Second\n\nturn.")
        assertThat(parsed.threads.single().messages.map { it.sender }).containsExactly("human", "assistant")
        assertThat(parsed.threads.single().messages.map { it.createdAt }).containsExactly(
            Instant.parse("2024-01-01T10:00:00.123Z"), Instant.parse("2024-01-01T10:00:01.456Z")
        )
        assertThat(parsed.skippedBlocks).isEqualTo(3)
        assertThat(parsed.skippedAttachments).isEqualTo(1)
        assertThat(parsed.skippedFiles).isEqualTo(1)
        assertThat(parsed.skippedTurns).isEqualTo(1)
    }

    @Test
    fun `a linear conversation is one thread in source order`() {
        val parsed = ClaudeExportParser().parse(fixture, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

        assertThat(parsed.threads.single().leafUuid).isEqualTo("33333333-3333-4333-8333-333333333333")
        assertThat(parsed.threads.single().messages.map { it.uuid }).containsExactly(
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
        )
        assertThat(parsed.threads.single().pathEndsAtOmittedParent).isTrue()
    }

    @Test
    fun `conversation name selects one conversation`() {
        val parsed = ClaudeExportParser().parse(fixture, "Sanitized import fixture")

        assertThat(parsed.uuid).isEqualTo("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    }

    @Test
    fun `an untitled conversation does not block a UUID selection`() {
        val parser = ClaudeExportParser()

        assertThat(parser.parse(fixture, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa").name)
            .isEqualTo("Sanitized import fixture")
        assertThat(parser.parse(fixture, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb").name)
            .isEmpty()
        assertThat(parser.parse(fixture, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb").threads).isEmpty()
    }

    @Test
    fun `unknown selector names the missing conversation`() {
        assertThatThrownBy { ClaudeExportParser().parse(fixture, "absent") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("absent")
    }

    @Test
    fun `an empty text block does not select the flat text`() {
        val parsed = ClaudeExportParser().parse(export("""[{"type":"text","text":""}]"""), "One")

        assertThat(parsed.threads.single().messages).isEmpty()
        assertThat(parsed.skippedTurns).isEqualTo(1)
    }

    @Test
    fun `absent content uses flat text`() {
        val file = export("[]", "Flat text")
        Files.writeString(file, Files.readString(file).replace(",\"content\":[]", ""))

        val parsed = ClaudeExportParser().parse(file, "One")

        assertThat(parsed.threads.single().messages.single().text).isEqualTo("Flat text")
    }

    @Test
    fun `a nonarray content field is rejected`() {
        assertThatThrownBy { ClaudeExportParser().parse(export("{}"), "One") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("content")
    }

    @Test
    fun `duplicate conversation names are ambiguous`() {
        val raw = Files.readString(fixture)
        val item = raw.trim().removePrefix("[").removeSuffix("]")
        val file = temp.resolve("duplicate.json")
        Files.writeString(file, "[$item,$item]")

        assertThatThrownBy { ClaudeExportParser().parse(file, "Sanitized import fixture") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("found 2")
    }

    @Test
    fun `a forked conversation gives one thread per leaf`() {
        val parsed = ClaudeExportParser().parse(forkedExport(), "Forked")

        assertThat(parsed.threads.map { it.leafUuid }).containsExactly(
            "22222222-2222-4222-8222-222222222222",
            "33333333-3333-4333-8333-333333333333",
        )
        assertThat(parsed.threads.map { it.messages.map { turn -> turn.uuid } }).containsExactly(
            listOf("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"),
            listOf("11111111-1111-4111-8111-111111111111", "33333333-3333-4333-8333-333333333333"),
        )
        assertThat(parsed.threads.map { it.pathEndsAtOmittedParent }).containsExactly(false, false)
    }

    @Test
    fun `a shared parent record appears in every thread that uses it`() {
        val parsed = ClaudeExportParser().parse(forkedExport(), "Forked")

        assertThat(parsed.threads.map { thread -> thread.messages.first().uuid })
            .containsExactly("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111")
    }

    @Test
    fun `a thread holds no record of another branch`() {
        val parsed = ClaudeExportParser().parse(forkedExport(), "Forked")

        assertThat(parsed.threads.first().messages.map { it.uuid })
            .doesNotContain("33333333-3333-4333-8333-333333333333")
        assertThat(parsed.threads.last().messages.map { it.uuid })
            .doesNotContain("22222222-2222-4222-8222-222222222222")
    }

    @Test
    fun `a thread reports the parent that the export omits`() {
        val parsed = ClaudeExportParser().parse(forkedExport("99999999-9999-4999-8999-999999999999"), "Forked")

        assertThat(parsed.threads.map { it.leafUuid }).containsExactly(
            "22222222-2222-4222-8222-222222222222",
            "33333333-3333-4333-8333-333333333333",
        )
        assertThat(parsed.threads.map { it.pathEndsAtOmittedParent }).containsExactly(true, true)
    }

    @Test
    fun `a parent cycle is rejected`() {
        val file = temp.resolve("cycle.json")
        Files.writeString(file, """[
          {"uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"Cycle","chat_messages":[
            {"uuid":"11111111-1111-4111-8111-111111111111","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"One","parent_message_uuid":"22222222-2222-4222-8222-222222222222"},
            {"uuid":"22222222-2222-4222-8222-222222222222","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"Two","parent_message_uuid":"11111111-1111-4111-8111-111111111111"}
          ]}
        ]""")

        assertThatThrownBy { ClaudeExportParser().parse(file, "Cycle") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cycle")
    }
}