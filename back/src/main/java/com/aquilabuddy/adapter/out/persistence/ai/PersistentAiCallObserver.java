package com.aquilabuddy.adapter.out.persistence.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * AI 호출 관측 이벤트를 DB에 저장합니다.
 */
@Component
public class PersistentAiCallObserver implements AiCallObserver {

    private final AiCallObservationJpaRepository repository;

    public PersistentAiCallObserver(AiCallObservationJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
    }

    @Override
    public void record(AiCallObservation observation) {
        try {
            repository.save(AiCallObservationJpaEntity.from(Objects.requireNonNull(observation, "observation must not be null.")));
        } catch (RuntimeException exception) {
            // 관측 저장 실패가 실제 AI 기능 호출을 막지 않도록 한다.
        }
    }
}
