package com.aquilabuddy.adapter.out.persistence.ai;

import com.aquilabuddy.application.port.out.ai.LoadAiCallObservabilityPort;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
    public AiCallObservabilityReport loadRecentAiCallObservability(int limit) {
        int requestedLimit = normalizeLimit(limit);
        List<AiCallObservationJpaEntity> entities = repository.findByOrderByObservationIdDesc(
            PageRequest.of(0, requestedLimit)
        );
        List<AiCallObservation> recentCalls = new ArrayList<>(entities.size());
        int successCount = 0;
        long totalLatencyMillis = 0L;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;

        for (AiCallObservationJpaEntity entity : entities) {
            AiCallObservation observation = toDomain(entity);
            recentCalls.add(observation);
            if (observation.success()) {
                successCount++;
            }
            totalLatencyMillis += observation.latencyMillis();
            if (observation.inputTokens() != null) {
                totalInputTokens += observation.inputTokens();
            }
            if (observation.outputTokens() != null) {
                totalOutputTokens += observation.outputTokens();
            }
        }

        int totalCount = recentCalls.size();
        int failureCount = totalCount - successCount;
        long averageLatencyMillis = totalCount == 0
            ? 0L
            : Math.round((double) totalLatencyMillis / totalCount);

        return new AiCallObservabilityReport(
            requestedLimit,
            totalCount,
            successCount,
            failureCount,
            averageLatencyMillis,
            totalInputTokens,
            totalOutputTokens,
            List.copyOf(recentCalls)
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
            entity.getFailureReason(),
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
