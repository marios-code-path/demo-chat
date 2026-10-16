package com.demo.chat.test.domain

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.IndexFieldKinds
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.MembershipRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndexIdentityQueryTests {
    private val converters = IndexSearchRequestConverters()

    @Test
    fun `identity converters name exact fields on each index`() {
        assertThat(converters.topicNameToQuery(ByStringRequest("cafe\u0301:room")))
            .isEqualTo(IndexSearchRequest("_x_name", "caf\u00e9:room", 100))
        assertThat(converters.userHandleToQuery(ByStringRequest("ADMIN")))
            .isEqualTo(IndexSearchRequest("_x_handle", "admin", 100))
        assertThat(converters.topicIdToQuery(ByIdRequest(30L)))
            .isEqualTo(IndexSearchRequest("_x_topic", "30", 100))
        assertThat(converters.authPrincipalToQuery(ByIdRequest(10L)))
            .isEqualTo(IndexSearchRequest("_x_pid", "10", 100))
        assertThat(converters.authTargetToQuery(ByIdRequest(11L)))
            .isEqualTo(IndexSearchRequest("_x_tid", "11", 100))
        assertThat(converters.membershipIdToQuery(ByIdRequest(12L)))
            .isEqualTo(IndexSearchRequest("_x_memberOf", "12", 100))
        assertThat(converters.membershipRequestToQuery(MembershipRequest(13L, 14L)))
            .isEqualTo(IndexSearchRequest("_x_memberPair", "13|14", 100))
        assertThat(converters.keyValueFieldToQuery("client_id", "id-with:parts"))
            .isEqualTo(IndexSearchRequest("_x_client_id", "id-with:parts", 100))
    }

    @Test
    fun `text data keeps its plain field and source text`() {
        assertThat(IndexFieldKinds.MESSAGE.query("data", "one:two"))
            .isEqualTo(IndexSearchRequest("data", "one:two", 100))
    }
}
