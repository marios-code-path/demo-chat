package com.demo.chat.test.vector.embedded

import com.demo.chat.config.vector.embedded.EmbeddedVectorStoreConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * The collection directory carries the identity.
 *
 * The collection takes its width from embeddingModel.dimensions(), and two
 * models rarely share a width. A separate directory per identity means a model
 * change cannot meet a collection of the wrong width.
 */
class EmbeddedStorageDirectoryTests {

    @Test
    fun `a configured path gains the identity segment`() {
        val base = Files.createTempDirectory("embedded-storage-test")

        assertThat(
            EmbeddedVectorStoreConfiguration.storageDirectoryFor(
                base.toString(),
                EmbeddingIdentity("acme-e5"),
            )
        ).isEqualTo(base.resolve("acme-e5"))
    }

    @Test
    fun `a configured path is created when it is absent`() {
        val base = Files.createTempDirectory("embedded-storage-test")
        val absent = base.resolve("deeper")

        val directory = EmbeddedVectorStoreConfiguration.storageDirectoryFor(
            absent.toString(),
            EmbeddingIdentity("acme-e5"),
        )

        assertThat(Files.isDirectory(directory)).isTrue()
    }

    @Test
    fun `an unset path gives a temporary directory that carries the identity`() {
        val directory = EmbeddedVectorStoreConfiguration.storageDirectoryFor(
            "",
            EmbeddingIdentity("acme-e5"),
        )

        assertThat(directory.fileName).isEqualTo(Path.of("acme-e5"))
        assertThat(Files.isDirectory(directory)).isTrue()
    }

    @Test
    fun `two identities give two directories`() {
        val base = Files.createTempDirectory("embedded-storage-test").toString()

        val first = EmbeddedVectorStoreConfiguration.storageDirectoryFor(base, EmbeddingIdentity("one"))
        val second = EmbeddedVectorStoreConfiguration.storageDirectoryFor(base, EmbeddingIdentity("two"))

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `the collection bean stores under the identity directory`() {
        // The tests above call the companion function. This test builds the
        // bean. A hardcoded path inside the bean would pass the others and
        // fail this one.
        val base = Files.createTempDirectory("embedded-wiring-test")

        ApplicationContextRunner()
            .withPropertyValues(
                "app.service.core.vector=embedded",
                "app.service.core.vector.embedded.path=$base",
            )
            .withBean(EmbeddingModel::class.java, { DummyEmbeddingModel() })
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity("wired-v1") })
            .withUserConfiguration(EmbeddedVectorStoreConfiguration::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(Files.isDirectory(base.resolve("wired-v1")))
                    .describedAs("the bean must store under the identity directory")
                    .isTrue()
            }
    }

    @Test
    fun `a reopened store retains vectors and accepts replacement and new writes`(@TempDir base: Path) {
        val firstId = "message:long:1"
        val secondId = "message:long:2"
        val runner = ApplicationContextRunner()
            .withPropertyValues(
                "app.service.core.vector=embedded",
                "app.service.core.vector.embedded.path=$base",
            )
            .withBean(EmbeddingModel::class.java, { DummyEmbeddingModel() })
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity("reopen-v1") })
            .withUserConfiguration(EmbeddedVectorStoreConfiguration::class.java)

        fun document(id: String, text: String): Document =
            Document.builder().id(id).text(text).metadata(mapOf("kind" to "message")).build()

        runner.run { context ->
            assertThat(context).hasNotFailed()
            context.getBean(VectorStore::class.java).add(listOf(document(firstId, "apple pie")))
        }

        runner.run { context ->
            assertThat(context).hasNotFailed()
            val store = context.getBean(VectorStore::class.java)
            val saved = store.similaritySearch(SearchRequest.builder().query("apple pie").topK(5).build())
            assertThat(saved.map { it.id }).containsExactly(firstId)

            store.delete(listOf(firstId))
            store.add(listOf(document(firstId, "pear tart")))
            store.add(listOf(document(secondId, "pear cake")))
        }

        runner.run { context ->
            assertThat(context).hasNotFailed()
            val saved = context.getBean(VectorStore::class.java)
                .similaritySearch(SearchRequest.builder().query("pear").topK(5).build())
            assertThat(saved.map { it.id }).containsExactlyInAnyOrder(firstId, secondId)
            assertThat(saved.map { it.text }).containsExactlyInAnyOrder("pear tart", "pear cake")
        }
    }
}
