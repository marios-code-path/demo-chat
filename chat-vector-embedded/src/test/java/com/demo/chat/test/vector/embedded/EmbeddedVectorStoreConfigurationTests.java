package com.demo.chat.test.vector.embedded;

import com.demo.chat.config.vector.embedded.EmbeddedVectorStoreConfiguration;
import com.demo.chat.service.dummy.DummyEmbeddingModel;
import com.integrallis.vectors.spring.ai.JavaVectorsVectorStore;
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

/**
 * Mirrors SimpleVectorStoreConfigurationTests, which pins the same three
 * behaviours for the simple provider.
 */
class EmbeddedVectorStoreConfigurationTests {

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", properties));
        }
        context.getBeanFactory().registerSingleton("embeddingModel", new DummyEmbeddingModel());
        context.register(EmbeddedVectorStoreConfiguration.class);
        context.refresh();
        return context;
    }

    @Test
    void vectorEmbeddedWithEmbeddingMockCreatesTheStoreBean(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "app.service.core.vector", "embedded",
                "app.service.core.vector.embedded.path", storage.toString()))) {

            assertThat(context.getBean(VectorStore.class))
                    .isInstanceOf(JavaVectorsVectorStore.class);
        }
    }

    @Test
    void vectorSelectorUnsetCreatesNoStoreBean() {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            assertThat(context.getBeanNamesForType(VectorStore.class)).isEmpty();
        }
    }

    @Test
    void embeddedStoreStoresAndRecallsMessageDocuments(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "app.service.core.vector", "embedded",
                "app.service.core.vector.embedded.path", storage.toString()))) {

            VectorStore store = context.getBean(VectorStore.class);
            store.add(List.of(
                    Document.builder().id("a").text("apple banana")
                            .metadata(Map.of("kind", "message")).build(),
                    Document.builder().id("b").text("zebra stripe")
                            .metadata(Map.of("kind", "message")).build()));

            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("apple banana").topK(2).build());

            assertThat(hits).isNotNull();
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).getId()).isEqualTo("a");
            assertThat(hits.get(0).getScore()).isNotNull();
        }
    }

    @Test
    void unsetPathStillCreatesAWorkingStore() {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "app.service.core.vector", "embedded"))) {

            VectorStore store = context.getBean(VectorStore.class);
            store.add(List.of(Document.builder().id("a").text("apple banana").build()));

            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("apple banana").topK(1).build());

            assertThat(hits).isNotEmpty();
        }
    }

    @Test
    void dimensionComesFromTheEmbeddingModel(@TempDir Path storage) {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "app.service.core.vector", "embedded",
                "app.service.core.vector.embedded.path", storage.toString()))) {

            VectorStore store = context.getBean(VectorStore.class);
            // DummyEmbeddingModel declares 256. A collection built at another
            // width rejects the add, so a successful add pins the wiring.
            store.add(List.of(Document.builder().id("a").text("apple banana").build()));

            assertThat(context.getBean(com.integrallis.vectors.db.VectorCollection.class).size())
                    .isEqualTo(1);
        }
    }
}
