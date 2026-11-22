package com.aquilabuddy.application.port.out.ai;

import com.aquilabuddy.domain.ai.AiCallObservabilityReport;

/**
 * 저장된 AI 호출 관측 이벤트를 운영 summary로 조회합니다.
 */
public interface LoadAiCallObservabilityPort {

    AiCallObservabilityReport loadRecentAiCallObservability(int limit);
}
