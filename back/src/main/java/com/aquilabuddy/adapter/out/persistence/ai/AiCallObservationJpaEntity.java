package com.aquilabuddy.adapter.out.persistence.ai;

import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * AI provider 호출 관측 이벤트를 저장하는 JPA 모델입니다.
 */
@Entity
@Table(name = "ai_call_observations")
public class AiCallObservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "observation_id")
    private Long observationId;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 40)
    private AiCallOperation operation;

    @Column(name = "model", nullable = false, length = 160)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 120)
    private String promptVersion;

    @Column(name = "latency_millis", nullable = false)
    private Long latencyMillis;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_type", length = 255)
    private String errorType;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected AiCallObservationJpaEntity() {
    }

    private AiCallObservationJpaEntity(AiCallObservation observation) {
        this.provider = observation.provider();
        this.operation = observation.operation();
        this.model = observation.model();
        this.promptVersion = observation.promptVersion();
        this.latencyMillis = observation.latencyMillis();
        this.success = observation.success();
        this.errorType = truncate(observation.errorType(), 255);
        this.inputTokens = observation.inputTokens();
        this.outputTokens = observation.outputTokens();
        this.observedAt = observation.observedAt();
    }

    static AiCallObservationJpaEntity from(AiCallObservation observation) {
        return new AiCallObservationJpaEntity(observation);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    Long getObservationId() {
        return observationId;
    }

    String getProvider() {
        return provider;
    }

    AiCallOperation getOperation() {
        return operation;
    }

    String getModel() {
        return model;
    }

    String getPromptVersion() {
        return promptVersion;
    }

    Long getLatencyMillis() {
        return latencyMillis;
    }

    boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    String getErrorType() {
        return errorType;
    }

    Integer getInputTokens() {
        return inputTokens;
    }

    Integer getOutputTokens() {
        return outputTokens;
    }

    Instant getObservedAt() {
        return observedAt;
    }
}
