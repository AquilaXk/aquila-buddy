package com.aquilabuddy.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.IntStream;
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
    void calculates1536DimensionUnrolledFmaCosineEquivalently() {
        double[] leftRaw = IntStream.range(0, 1536)
            .mapToDouble(index -> Math.sin(index * 0.13 + 0.5))
            .toArray();
        double[] rightRaw = IntStream.range(0, 1536)
            .mapToDouble(index -> Math.cos(index * 0.07 + 0.25))
            .toArray();

        EmbeddingVector left = new EmbeddingVector(leftRaw);
        EmbeddingVector right = new EmbeddingVector(rightRaw);

        double expectedDot = 0.0;
        double expectedNormA = 0.0;
        double expectedNormB = 0.0;
        for (int index = 0; index < 1536; index++) {
            expectedDot += leftRaw[index] * rightRaw[index];
            expectedNormA += leftRaw[index] * leftRaw[index];
            expectedNormB += rightRaw[index] * rightRaw[index];
        }
        double expectedCosine = expectedDot / (Math.sqrt(expectedNormA) * Math.sqrt(expectedNormB));

        assertThat(left.cosineSimilarity(right)).isCloseTo(expectedCosine, within(1e-12));
        assertThat(left.cosineSimilarity(left)).isCloseTo(1.0, within(1e-12));
        assertThat(left.values()).isUnmodifiable();
    }

    @Test
    void rejectsZeroNonFiniteAndExcessiveDimensionVectors() {
        assertThatThrownBy(() -> new EmbeddingVector(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("values");
        assertThatThrownBy(() -> new EmbeddingVector(new double[] {0.0, 0.0, 0.0}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zero vector");
        assertThatThrownBy(() -> new EmbeddingVector(new double[] {Double.NaN, 1.0}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("finite");
        assertThatThrownBy(() -> new EmbeddingVector(new double[9_000]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimension exceeds limit");

        EmbeddingVector vector = new EmbeddingVector(List.of(1.0, 0.0));
        assertThatThrownBy(() -> vector.cosineSimilarity(new EmbeddingVector(List.of(1.0))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimension");
    }
}
