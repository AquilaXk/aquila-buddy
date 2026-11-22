package com.aquilabuddy.adapter.out.persistence.prdraft;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PR 초안 번역 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface PullRequestDraftTranslationJpaRepository
    extends JpaRepository<PullRequestDraftTranslationJpaEntity, String> {

    /**
     * 특정 PR 초안에서 생성된 번역 결과를 최근 생성순으로 반환합니다.
     */
    List<PullRequestDraftTranslationJpaEntity> findAllByDraftIdOrderByCreatedAtDesc(String draftId);

    /**
     * Dashboard 활동에 표시할 번역 결과를 전체 초안 기준 최근 생성순으로 반환합니다.
     */
    List<PullRequestDraftTranslationJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
