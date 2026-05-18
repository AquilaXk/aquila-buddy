package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.embedding.EmbeddingVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexAiEmbeddingClientTest {

    @Test
    void delegatesInputToVertexEmbeddingGenerator() {
        CapturingVertexAiEmbeddingGenerator generator = new CapturingVertexAiEmbeddingGenerator(List.of(0.1, -0.2, 0.3));
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(generator);

        EmbeddingVector vector = client.embed("Java Spring contribution");

        assertThat(vector.values()).containsExactly(0.1, -0.2, 0.3);
        assertThat(generator.input()).isEqualTo("Java Spring contribution");
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
        VertexAiEmbeddingClient client = new VertexAiEmbeddingClient(new CapturingVertexAiEmbeddingGenerator(List.of()));

        assertThatThrownBy(() -> client.embed("Java Spring contribution"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Vertex AI embedding vector");
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
