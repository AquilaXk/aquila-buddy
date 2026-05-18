package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 별도 저장 adapter가 없을 때 AI 호출 자체를 막지 않는 관측 fallback입니다.
 */
@Component
@ConditionalOnMissingBean(AiCallObserver.class)
public class NoopAiCallObserver implements AiCallObserver {

    @Override
    public void record(AiCallObservation observation) {
        // 관측 저장소가 없는 테스트/로컬 context에서는 호출 흐름만 유지합니다.
    }
}
