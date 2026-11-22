package com.aquilabuddy.application.port.out.ai;

import com.aquilabuddy.domain.ai.AiCallObservation;

/**
 * AI provider 호출 관측 이벤트를 외부 저장소나 로그로 전달하는 outbound port입니다.
 */
public interface AiCallObserver {

    void record(AiCallObservation observation);
}
