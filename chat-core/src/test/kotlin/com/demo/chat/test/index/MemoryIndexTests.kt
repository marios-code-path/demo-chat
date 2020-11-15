package com.demo.chat.test.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.memory.MemoryIndex
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.store.RAMDirectory
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test


class MemoryIndexTests {


    @Test
    fun `should index and query`() {
        val indexer = MemoryIndex()
        val analyzer = SimpleAnalyzer()

        indexer.addField("key", "23456", analyzer)
        indexer.addField("key", "12345", analyzer)
        indexer.addField("name", "mario josh james nichol", analyzer)

        val parser = QueryParser("name", analyzer)
        val score = indexer.search(parser.parse("+key:456 +nichol*"))

        Assertions
                .assertThat(score)
                .isGreaterThan(0.0f)

        println("score = $score")
        println("indexData=${indexer.toStringDebug()}")
    }

    @Test
    fun `should index 2 users`() {
        val users = listOf(
                User.create(Key.funKey(12345L), "mario", "darkbit", "localhost"),
                User.create(Key.funKey(23456L), "josh", "starbuxman", "localhost")
        )

        val analyzer = StandardAnalyzer()
        val directory = ByteBuffersDirectory()
        val config = IndexWriterConfig(analyzer)
        val indexWriter = IndexWriter(directory, config)

        Document().apply {
            users.forEach { user ->
                add(Field("name", user.name, TextField.TYPE_NOT_STORED))
                add(Field("handle", user.handle, TextField.TYPE_NOT_STORED))
                add(Field("key", user.key.id.toString(), TextField.TYPE_STORED))
            }
            indexWriter.addDocument(this)
        }

        indexWriter.close()

        val indexReader = DirectoryReader.open(directory)
        val indexSearcher = IndexSearcher(indexReader)

        val parser = QueryParser("name", analyzer)
        val query = parser.parse("mario")
        val hits = indexSearcher.search(query,100).scoreDocs

        Assertions
                .assertThat(hits.size)
                .isGreaterThan(0)

        for(hit in hits) {
            val hitDoc = indexSearcher.doc(hit.doc)

            Assertions
                    .assertThat(hitDoc.get("key"))
                    .isEqualTo("12345")
        }

        indexReader.close()
        directory.close()
    }
}