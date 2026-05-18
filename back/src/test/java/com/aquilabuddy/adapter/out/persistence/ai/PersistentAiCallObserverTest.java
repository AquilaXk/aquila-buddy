package com.aquilabuddy.adapter.out.persistence.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class PersistentAiCallObserverTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-05-18T05:30:00Z");

    @Autowired
    private AiCallObservationJpaRepository repository;

    private PersistentAiCallObserver observer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        observer = new PersistentAiCallObserver(repository);
    }

    @Test
    void persistsSuccessfulObservation() {
        observer.record(AiCallObservation.success(
            "openai-compatible",
            AiCallOperation.COMPLETION,
            "gpt-4o-mini",
            "ai-client.complete.v1",
            Duration.ofMillis(42),
            120,
            32,
            OBSERVED_AT
        ));

        assertThat(repository.findAllByOrderByObservationIdAsc())
            .extracting(
                AiCallObservationJpaEntity::getProvider,
                AiCallObservationJpaEntity::getOperation,
                AiCallObservationJpaEntity::getModel,
                AiCallObservationJpaEntity::getPromptVersion,
                AiCallObservationJpaEntity::getLatencyMillis,
                AiCallObservationJpaEntity::isSuccess,
                AiCallObservationJpaEntity::getErrorType,
                AiCallObservationJpaEntity::getInputTokens,
                AiCallObservationJpaEntity::getOutputTokens,
                AiCallObservationJpaEntity::getObservedAt
            )
            .containsExactly(org.assertj.core.groups.Tuple.tuple(
                "openai-compatible",
                AiCallOperation.COMPLETION,
                "gpt-4o-mini",
                "ai-client.complete.v1",
                42L,
                true,
                null,
                120,
                32,
                OBSERVED_AT
            ));
    }

    @Test
    void persistsFailedObservation() {
        observer.record(AiCallObservation.failure(
            "vertex-ai",
            AiCallOperation.EMBEDDING,
            "gemini-embedding-001",
            "ai-client.embed.v1",
            Duration.ofMillis(105),
            "IllegalStateException",
            OBSERVED_AT
        ));

        AiCallObservationJpaEntity saved = repository.findAllByOrderByObservationIdAsc().getFirst();
        assertThat(saved.getProvider()).isEqualTo("vertex-ai");
        assertThat(saved.getOperation()).isEqualTo(AiCallOperation.EMBEDDING);
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getErrorType()).isEqualTo("IllegalStateException");
        assertThat(saved.getInputTokens()).isNull();
        assertThat(saved.getOutputTokens()).isNull();
    }
}
