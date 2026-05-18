package com.aquilabuddy.adapter.out.persistence.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 호출 관측 이벤트를 DB에 저장합니다.
 */
@Component
public class PersistentAiCallObserver implements AiCallObserver {

    private static final Logger log = LoggerFactory.getLogger(PersistentAiCallObserver.class);

    private final AiCallObservationJpaRepository repository;

    public PersistentAiCallObserver(AiCallObservationJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
    }

    @Override
    public void record(AiCallObservation observation) {
        try {
            repository.save(AiCallObservationJpaEntity.from(Objects.requireNonNull(observation, "observation must not be null.")));
        } catch (RuntimeException exception) {
            log.warn("AI call observation could not be persisted.", exception);
        }
    }
}
