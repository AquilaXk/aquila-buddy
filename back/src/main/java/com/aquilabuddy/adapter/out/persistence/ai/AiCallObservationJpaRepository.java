package com.aquilabuddy.adapter.out.persistence.ai;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 호출 관측 이벤트 저장소입니다.
 */
public interface AiCallObservationJpaRepository extends JpaRepository<AiCallObservationJpaEntity, Long> {

    List<AiCallObservationJpaEntity> findAllByOrderByObservationIdAsc();
}
