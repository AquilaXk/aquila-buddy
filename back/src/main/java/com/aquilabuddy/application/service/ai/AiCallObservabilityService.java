package com.aquilabuddy.application.service.ai;

import com.aquilabuddy.application.port.in.ai.GetAiCallObservabilityUseCase;
import com.aquilabuddy.application.port.out.ai.LoadAiCallObservabilityPort;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 저장된 AI 호출 관측 이벤트를 운영 조회용 summary로 반환합니다.
 */
@Service
public class AiCallObservabilityService implements GetAiCallObservabilityUseCase {

    private final LoadAiCallObservabilityPort loadAiCallObservabilityPort;

    public AiCallObservabilityService(LoadAiCallObservabilityPort loadAiCallObservabilityPort) {
        this.loadAiCallObservabilityPort = loadAiCallObservabilityPort;
    }

    @Override
    public AiCallObservabilityReport getRecentAiCallObservability(int limit) {
        return loadAiCallObservabilityPort.loadRecentAiCallObservability(limit);
    }
}
