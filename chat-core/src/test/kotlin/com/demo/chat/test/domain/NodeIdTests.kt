package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NodeIdTests {

    @Test
    fun `parse accepts zero as an explicit value`() {
        Assertions.assertEquals(0, NodeId.parse("0").value)
    }

    @Test
    fun `parse accepts the maximum value`() {
        Assertions.assertEquals(1023, NodeId.parse("1023").value)
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        Assertions.assertEquals(7, NodeId.parse("  7  ").value)
    }

    @Test
    fun `parse rejects an unset value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse(null)
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid is required"))
        Assertions.assertTrue(thrown.message!!.contains("unset"))
    }

    @Test
    fun `parse rejects an empty value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("")
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid is required"))
    }

    @Test
    fun `parse rejects a whitespace only value`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("   ")
        }
    }

    @Test
    fun `parse rejects a non numeric value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("abc")
        }
        Assertions.assertTrue(thrown.message!!.contains("abc"))
    }

    @Test
    fun `parse rejects a negative value`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("-1")
        }
    }

    @Test
    fun `parse rejects a value above the maximum`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("1024")
        }
        Assertions.assertTrue(thrown.message!!.contains("0..1023"))
    }

    @Test
    fun `the message names the property and the range`() {
        val text = NodeId.message("99999")
        Assertions.assertTrue(text.contains("app.nodeid"))
        Assertions.assertTrue(text.contains("0..1023"))
        Assertions.assertTrue(text.contains("no default"))
        Assertions.assertTrue(text.contains("99999"))
    }

    @Test
    fun `the message calls a null value unset`() {
        val text = NodeId.message(null)
        Assertions.assertTrue(text.contains("unset"))
    }

    @Test
    fun `the message shows a blank value in quotes rather than as unset`() {
        val text = NodeId.message("   ")
        Assertions.assertTrue(text.contains("'   '"), text)
        Assertions.assertFalse(text.contains("unset"), text)
    }
}
