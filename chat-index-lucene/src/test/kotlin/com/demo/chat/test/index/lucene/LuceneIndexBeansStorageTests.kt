package com.demo.chat.test.index.lucene

import com.demo.chat.config.LuceneIndexBeans
import com.demo.chat.domain.LongUtil
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import java.nio.file.Path

/**
 * These tests call luceneStorage() alone. An instance built without Spring has
 * no bean proxy, so two calls of one bean method give two objects. A test that
 * needs one index across two bean methods must use a Spring context.
 */
class LuceneIndexBeansStorageTests {

    private val entries: ObjectProvider<KeyValueIndexFieldsEntry> =
        StaticListableBeanFactory().getBeanProvider(KeyValueIndexFieldsEntry::class.java)

    private fun beans(root: String?) = LuceneIndexBeans(LongUtil(), FakeKeyServices.longRoots(), entries, root, "long", 7)

    @Test
    fun `no root gives memory storage`() {
        assertThat(beans(null).luceneStorage()).isInstanceOf(MemoryStorage::class.java)
    }

    @Test
    fun `a root gives file storage`(@TempDir root: Path) {
        assertThat(beans(root.toString()).luceneStorage()).isInstanceOf(FileStorage::class.java)
    }

    @Test
    fun `a blank root fails`() {
        assertThatThrownBy { beans(" ").luceneStorage() }.hasMessageContaining("app.index.lucene.root")
    }
}
