package com.aquilabuddy.adapter.out.persistence.ai;

import com.aquilabuddy.application.port.out.ai.LoadAiCallObservabilityPort;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 저장된 AI 호출 관측 이벤트를 최근 호출 summary로 변환합니다.
 */
@Component
public class AiCallObservabilityPersistenceAdapter implements LoadAiCallObservabilityPort {

    private final AiCallObservationJpaRepository repository;

    public AiCallObservabilityPersistenceAdapter(AiCallObservationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiCallObservabilityReport loadRecentAiCallObservability(int limit) {
        int requestedLimit = normalizeLimit(limit);
        List<AiCallObservation> recentCalls = repository.findByOrderByObservationIdDesc(PageRequest.of(0, requestedLimit))
            .stream()
            .map(this::toDomain)
            .toList();
        int successCount = (int) recentCalls.stream().filter(AiCallObservation::success).count();
        int failureCount = recentCalls.size() - successCount;
        long averageLatencyMillis = recentCalls.isEmpty()
            ? 0
            : Math.round(recentCalls.stream().mapToLong(AiCallObservation::latencyMillis).average().orElse(0));
        int totalInputTokens = recentCalls.stream()
            .map(AiCallObservation::inputTokens)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
        int totalOutputTokens = recentCalls.stream()
            .map(AiCallObservation::outputTokens)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();

        return new AiCallObservabilityReport(
            requestedLimit,
            recentCalls.size(),
            successCount,
            failureCount,
            averageLatencyMillis,
            totalInputTokens,
            totalOutputTokens,
            recentCalls
        );
    }

    private AiCallObservation toDomain(AiCallObservationJpaEntity entity) {
        return new AiCallObservation(
            entity.getProvider(),
            entity.getOperation(),
            entity.getModel(),
            entity.getPromptVersion(),
            entity.getLatencyMillis(),
            entity.isSuccess(),
            entity.getErrorType(),
            entity.getInputTokens(),
            entity.getOutputTokens(),
            entity.getObservedAt()
        );
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 200);
    }
}
