package com.demo.chat.test.vector.embedded;

import com.demo.chat.config.vector.embedded.EmbeddedVectorStoreConfiguration;
import com.demo.chat.service.dummy.DummyEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The two store rules that the vector indexer depends on.
 *
 * The indexer removes a document id and then writes it. Both halves of that
 * pair rest on provider behaviour, and a double cannot establish either one.
 */
class EmbeddedVectorStoreReplaceTests {

    private AnnotationConfigApplicationContext context(Path storage) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.service.core.vector", "embedded",
                "app.service.core.vector.embedded.path", storage.toString())));
        context.getBeanFactory().registerSingleton("embeddingModel", new DummyEmbeddingModel());
        context.register(EmbeddedVectorStoreConfiguration.class);
        context.refresh();
        return context;
    }

    private Document document(String id, String text) {
        return Document.builder().id(id).text(text).metadata(Map.of("kind", "message")).build();
    }

    /**
     * A removal of an unknown id must not throw.
     *
     * The indexer removes before every write, including the first one. A
     * provider that threw here would break a first add rather than repair a
     * repeat.
     */
    @Test
    void deletingAnUnknownIdDoesNotThrow(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(storage)) {
            VectorStore store = context.getBean(VectorStore.class);

            assertThatCode(() -> store.delete(List.of("message:long:404"))).doesNotThrowAnyException();
        }
    }

    /**
     * A repeated write of one id is refused.
     *
     * This is the defect the indexer repair exists for. Every rebuild after
     * the first one met this exception.
     */
    @Test
    void writingOneIdTwiceIsRefused(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(storage)) {
            VectorStore store = context.getBean(VectorStore.class);
            store.add(List.of(document("message:long:1", "apple pie")));

            assertThatThrownBy(() -> store.add(List.of(document("message:long:1", "pear tart"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate id");
        }
    }

    /**
     * A removal before the write makes the repeat succeed, and the new text
     * replaces the old one.
     */
    @Test
    void removingBeforeWritingReplacesTheDocument(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(storage)) {
            VectorStore store = context.getBean(VectorStore.class);
            store.add(List.of(document("message:long:1", "apple pie")));

            store.delete(List.of("message:long:1"));
            store.add(List.of(document("message:long:1", "pear tart")));

            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("pear tart").topK(5).build());
            assertThat(hits).isNotNull();
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getText()).isEqualTo("pear tart");
        }
    }
}
