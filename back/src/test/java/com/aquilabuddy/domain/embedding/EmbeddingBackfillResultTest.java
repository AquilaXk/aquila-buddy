package com.aquilabuddy.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingBackfillResultTest {

    @Test
    void keepsFailureReasonSamplesImmutableAndTrimmed() {
        List<String> reasons = new java.util.ArrayList<>(List.of(
            " issue-1: IllegalStateException ",
            "",
            "issue-2: expected 1536 but was 768"
        ));

        EmbeddingBackfillResult result = new EmbeddingBackfillResult(
            "issues",
            10,
            2,
            0,
            0,
            0,
            0,
            1,
            1,
            reasons
        );
        reasons.add("issue-3: late mutation");

        assertThat(result.failureReasons())
            .containsExactly(
                "issue-1: IllegalStateException",
                "issue-2: expected 1536 but was 768"
            );
        assertThatThrownBy(() -> result.failureReasons().add("issue-4"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsLegacyConstructorWithEmptyFailureReasons() {
        EmbeddingBackfillResult result = new EmbeddingBackfillResult("profiles", 5, 1, 1, 0, 0, 0, 0, 0);

        assertThat(result.failureReasons()).isEmpty();
    }
}
