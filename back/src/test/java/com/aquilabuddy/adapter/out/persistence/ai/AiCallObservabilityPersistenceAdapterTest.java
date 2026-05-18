package com.aquilabuddy.adapter.out.persistence.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AiCallObservabilityPersistenceAdapterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-05-18T06:00:00Z");

    @Autowired
    private AiCallObservationJpaRepository repository;

    private PersistentAiCallObserver observer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        observer = new PersistentAiCallObserver(repository);
    }

    @Test
    void loadsRecentAiCallObservabilityReportWithSummary() {
        observer.record(AiCallObservation.success(
            "openai-compatible",
            AiCallOperation.COMPLETION,
            "gpt-4o-mini",
            "ai-client.complete.v1",
            Duration.ofMillis(30),
            100,
            20,
            BASE_TIME
        ));
        observer.record(AiCallObservation.failure(
            "vertex-ai",
            AiCallOperation.EMBEDDING,
            "gemini-embedding-001",
            "ai-client.embed.v1",
            Duration.ofMillis(90),
            "IllegalStateException",
            BASE_TIME.plusSeconds(1)
        ));
        observer.record(AiCallObservation.success(
            "openai-compatible",
            AiCallOperation.EMBEDDING,
            "text-embedding-3-small",
            "ai-client.embed.v1",
            Duration.ofMillis(60),
            12,
            null,
            BASE_TIME.plusSeconds(2)
        ));
        AiCallObservabilityPersistenceAdapter adapter = new AiCallObservabilityPersistenceAdapter(repository);

        AiCallObservabilityReport report = adapter.loadRecentAiCallObservability(2);

        assertThat(report.requestedLimit()).isEqualTo(2);
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.successCount()).isEqualTo(1);
        assertThat(report.failureCount()).isEqualTo(1);
        assertThat(report.averageLatencyMillis()).isEqualTo(75);
        assertThat(report.totalInputTokens()).isEqualTo(12);
        assertThat(report.totalOutputTokens()).isZero();
        assertThat(report.recentCalls())
            .extracting(AiCallObservation::model)
            .containsExactly("text-embedding-3-small", "gemini-embedding-001");
    }
}
