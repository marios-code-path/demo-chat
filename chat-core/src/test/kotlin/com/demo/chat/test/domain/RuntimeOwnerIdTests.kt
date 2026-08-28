package com.demo.chat.test.domain

import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RuntimeOwnerIdTests {

    @Test
    fun `the value names the application the host and the process`() {
        val owner = RuntimeOwnerId.generate("core-service").value
        Assertions.assertTrue(owner.startsWith("core-service@"), owner)
        Assertions.assertTrue(owner.contains(":"), owner)
        Assertions.assertTrue(owner.contains("#"), owner)
    }

    @Test
    fun `two values generated in one process differ`() {
        Assertions.assertNotEquals(
            RuntimeOwnerId.generate("core-service").value,
            RuntimeOwnerId.generate("core-service").value
        )
    }

    @Test
    fun `the random suffix is eight hex characters`() {
        val suffix = RuntimeOwnerId.generate("core-service").value.substringAfterLast("#")
        Assertions.assertTrue(suffix.matches(Regex("[0-9a-f]{8}")), suffix)
    }
}
