package com.aquilabuddy.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {

    @Test
    void normalizesValuesAndExposesPgvectorLiteral() {
        EmbeddingVector vector = new EmbeddingVector(List.of(1.0, 0.5, -0.25));

        assertThat(vector.dimension()).isEqualTo(3);
        assertThat(vector.values()).containsExactly(1.0, 0.5, -0.25);
        assertThat(vector.toPgvectorLiteral()).isEqualTo("[1.0,0.5,-0.25]");
    }

    @Test
    void calculatesCosineSimilarity() {
        EmbeddingVector left = new EmbeddingVector(List.of(1.0, 0.0));
        EmbeddingVector right = new EmbeddingVector(List.of(1.0, 1.0));

        assertThat(left.cosineSimilarity(right)).isCloseTo(0.7071, within(0.0001));
    }

    @Test
    void rejectsBlankOrDifferentDimensionVectors() {
        assertThatThrownBy(() -> new EmbeddingVector(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("values");

        EmbeddingVector vector = new EmbeddingVector(List.of(1.0, 0.0));
        assertThatThrownBy(() -> vector.cosineSimilarity(new EmbeddingVector(List.of(1.0))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimension");
    }
}
