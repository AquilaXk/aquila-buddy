package com.aquilabuddy.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiCallObservationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-05-18T05:20:00Z");

    @Test
    void createsSuccessfulCompletionObservation() {
        AiCallObservation observation = AiCallObservation.success(
            "openai-compatible",
            AiCallOperation.COMPLETION,
            "gpt-4o-mini",
            "ai-client.complete.v1",
            Duration.ofMillis(42),
            120,
            32,
            OBSERVED_AT
        );

        assertThat(observation.provider()).isEqualTo("openai-compatible");
        assertThat(observation.operation()).isEqualTo(AiCallOperation.COMPLETION);
        assertThat(observation.model()).isEqualTo("gpt-4o-mini");
        assertThat(observation.promptVersion()).isEqualTo("ai-client.complete.v1");
        assertThat(observation.latencyMillis()).isEqualTo(42);
        assertThat(observation.success()).isTrue();
        assertThat(observation.errorType()).isNull();
        assertThat(observation.failureReason()).isNull();
        assertThat(observation.inputTokens()).isEqualTo(120);
        assertThat(observation.outputTokens()).isEqualTo(32);
        assertThat(observation.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void createsFailureEmbeddingObservationWithErrorType() {
        AiCallObservation observation = AiCallObservation.failure(
            "vertex-ai",
            AiCallOperation.EMBEDDING,
            "gemini-embedding-001",
            "ai-client.embed.v1",
            Duration.ofMillis(105),
            "IllegalStateException",
            "Embedding provider returned invalid vector dimensions",
            OBSERVED_AT
        );

        assertThat(observation.provider()).isEqualTo("vertex-ai");
        assertThat(observation.operation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(observation.latencyMillis()).isEqualTo(105);
        assertThat(observation.success()).isFalse();
        assertThat(observation.errorType()).isEqualTo("IllegalStateException");
        assertThat(observation.failureReason()).isEqualTo("Embedding provider returned invalid vector dimensions");
        assertThat(observation.inputTokens()).isNull();
        assertThat(observation.outputTokens()).isNull();
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> AiCallObservation.success(
            " ",
            AiCallOperation.COMPLETION,
            "gpt-4o-mini",
            "ai-client.complete.v1",
            Duration.ofMillis(1),
            null,
            null,
            OBSERVED_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider");
    }
}
