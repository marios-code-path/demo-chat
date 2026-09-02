package com.demo.chat.test.domain

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.InvalidRecallRequestException
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecallRequestValidationTests {

    @Test
    fun `blank query fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "   ").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { UserRecallRequest(7L, "").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { GlobalRecallRequest(" ").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit defaults to 10`() {
        assertThat(TopicRecallRequest(3L, "apple").limit).isEqualTo(10)
        assertThat(UserRecallRequest(7L, "apple").limit).isEqualTo(10)
        assertThat(GlobalRecallRequest("apple").limit).isEqualTo(10)
    }

    @Test
    fun `limit below 1 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = 0).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = -1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit above 50 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = 51).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit at the bounds passes`() {
        TopicRecallRequest(3L, "apple", limit = 1).validate()
        TopicRecallRequest(3L, "apple", limit = 50).validate()
    }

    @Test
    fun `threshold defaults to 0_0`() {
        assertThat(TopicRecallRequest(3L, "apple").threshold).isEqualTo(0.0)
        assertThat(GlobalRecallRequest("apple").threshold).isEqualTo(0.0)
    }

    @Test
    fun `threshold outside 0_0 to 1_0 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", threshold = 1.1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { UserRecallRequest(7L, "apple", threshold = -0.1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { GlobalRecallRequest("apple", threshold = 2.0).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `threshold at the bounds passes`() {
        TopicRecallRequest(3L, "apple", threshold = 0.0).validate()
        TopicRecallRequest(3L, "apple", threshold = 1.0).validate()
    }
}