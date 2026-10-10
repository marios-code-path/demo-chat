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

    @Test
    fun `content text blocks join and flat text handles empty content`() {
        val parsed = ClaudeExportParser().parse(fixture, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

        assertThat(parsed.messages.map { it.text }).containsExactly("First turn.", "Second\n\nturn.")
        assertThat(parsed.messages.map { it.sender }).containsExactly("human", "assistant")
        assertThat(parsed.messages.map { it.createdAt }).containsExactly(
            Instant.parse("2024-01-01T10:00:00.123Z"), Instant.parse("2024-01-01T10:00:01.456Z")
        )
        assertThat(parsed.skippedBlocks).isEqualTo(2)
        assertThat(parsed.skippedAttachments).isEqualTo(1)
        assertThat(parsed.skippedTurns).isEqualTo(1)
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

        assertThat(parsed.messages).isEmpty()
        assertThat(parsed.skippedTurns).isEqualTo(1)
    }

    @Test
    fun `absent content uses flat text`() {
        val file = export("[]", "Flat text")
        Files.writeString(file, Files.readString(file).replace(",\"content\":[]", ""))

        val parsed = ClaudeExportParser().parse(file, "One")

        assertThat(parsed.messages.single().text).isEqualTo("Flat text")
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
}
