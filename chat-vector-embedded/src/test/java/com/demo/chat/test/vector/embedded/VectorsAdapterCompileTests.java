package com.demo.chat.test.vector.embedded;

import com.demo.chat.service.dummy.DummyEmbeddingModel;
import com.integrallis.vectors.core.SimilarityFunction;
import com.integrallis.vectors.db.IndexType;
import com.integrallis.vectors.db.VectorCollection;
import com.integrallis.vectors.spring.ai.JavaVectorsVectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile and round trip proof for the Vectors Spring AI adapter.
 *
 * <p>Two earlier spikes read the published metadata only. Neither compiled the
 * adapter. This test binds the adapter to the Spring AI surface that the root
 * BOM pins at 1.0.3, and stores and reads one document.
 */
class VectorsAdapterCompileTests {

    private static final int DIMENSIONS = 256;

    private VectorCollection collection(Path storage) {
        return VectorCollection.builder()
                .dimension(DIMENSIONS)
                .metric(SimilarityFunction.COSINE)
                .indexType(IndexType.FLAT)
                .storagePath(storage)
                .build();
    }

    @Test
    void adapterBuildsAVectorStore(@TempDir Path storage) throws Exception {
        try (VectorCollection collection = collection(storage)) {
            VectorStore store = JavaVectorsVectorStore
                    .builder(new DummyEmbeddingModel(), collection)
                    .collectionName("messages")
                    .build();

            assertThat(store).isInstanceOf(JavaVectorsVectorStore.class);
        }
    }

    @Test
    void storeAddsAndRecallsMessageDocuments(@TempDir Path storage) throws Exception {
        try (VectorCollection collection = collection(storage)) {
            VectorStore store = JavaVectorsVectorStore
                    .builder(new DummyEmbeddingModel(), collection)
                    .collectionName("messages")
                    .commitAfterAdd(true)
                    .build();

            store.add(List.of(
                    Document.builder().id("a").text("apple banana")
                            .metadata(java.util.Map.of("kind", "message")).build(),
                    Document.builder().id("b").text("zebra stripe")
                            .metadata(java.util.Map.of("kind", "message")).build()));

            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("apple banana").topK(2).build());

            assertThat(hits).isNotNull();
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).getId()).isEqualTo("a");
            assertThat(hits.get(0).getScore()).isNotNull();
        }
    }

    @Test
    void storagePathHoldsTheCollectionAfterCommit(@TempDir Path storage) throws Exception {
        try (VectorCollection collection = collection(storage)) {
            VectorStore store = JavaVectorsVectorStore
                    .builder(new DummyEmbeddingModel(), collection)
                    .collectionName("messages")
                    .commitAfterAdd(true)
                    .build();

            store.add(List.of(Document.builder().id("a").text("apple banana").build()));

            assertThat(storage.toFile().list()).isNotEmpty();
        }
    }
}
