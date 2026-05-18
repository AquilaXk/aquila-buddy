package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.ai.AiCallOperation;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexAiEmbeddingClientTest {

    @Test
    void delegatesInputToVertexEmbeddingGenerator() {
        CapturingVertexAiEmbeddingGenerator generator = new CapturingVertexAiEmbeddingGenerator(List.of(0.1, -0.2, 0.3));
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(generator, observer, "gemini-embedding-001");

        EmbeddingVector vector = client.embed("Java Spring contribution");

        assertThat(vector.values()).containsExactly(0.1, -0.2, 0.3);
        assertThat(generator.input()).isEqualTo("Java Spring contribution");
        assertThat(observer.firstObservation().provider()).isEqualTo("vertex-ai");
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observer.firstObservation().model()).isEqualTo("gemini-embedding-001");
        assertThat(observer.firstObservation().promptVersion()).isEqualTo("ai-client.embed.v1");
        assertThat(observer.firstObservation().success()).isTrue();
    }

    @Test
    void rejectsBlankInputBeforeCallingVertex() {
        CapturingVertexAiEmbeddingGenerator generator = new CapturingVertexAiEmbeddingGenerator(List.of(0.1));
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(generator);

        assertThatThrownBy(() -> client.embed(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input");
        assertThat(generator.callCount()).isZero();
    }

    @Test
    void rejectsEmptyGeneratedVector() {
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(
            new CapturingVertexAiEmbeddingGenerator(List.of()),
            observer,
            "gemini-embedding-001"
        );

        assertThatThrownBy(() -> client.embed("Java Spring contribution"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Vertex AI embedding vector");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().errorType()).isEqualTo("IllegalStateException");
        assertThat(observer.firstObservation().failureReason()).contains("Vertex AI embedding vector");
    }

    @Test
    void rejectsGeneratedVectorWhenDimensionDoesNotMatchConfiguredDimension() {
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(
            new CapturingVertexAiEmbeddingGenerator(List.of(0.1, -0.2)),
            observer,
            "gemini-embedding-001",
            3
        );

        assertThatThrownBy(() -> client.embed("Java Spring contribution"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dimension");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observer.firstObservation().errorType()).isEqualTo("IllegalStateException");
        assertThat(observer.firstObservation().failureReason())
            .contains("dimension")
            .contains("3")
            .contains("2");
    }

    private static final class CapturingVertexAiEmbeddingGenerator implements VertexAiEmbeddingGenerator {

        private final List<Double> response;
        private String input;
        private int callCount;

        private CapturingVertexAiEmbeddingGenerator(List<Double> response) {
            this.response = response;
        }

        @Override
        public List<Double> embed(String input) {
            this.input = input;
            this.callCount++;
            return response;
        }

        private String input() {
            return input;
        }

        private int callCount() {
            return callCount;
        }
    }
}
