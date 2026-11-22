package com.aquilabuddy.application.port.in.ai;

import com.aquilabuddy.domain.ai.AiCallObservabilityReport;

/**
 * 최근 AI provider 호출 관측 summary를 조회하는 use case입니다.
 */
public interface GetAiCallObservabilityUseCase {

    AiCallObservabilityReport getRecentAiCallObservability(int limit);
}
