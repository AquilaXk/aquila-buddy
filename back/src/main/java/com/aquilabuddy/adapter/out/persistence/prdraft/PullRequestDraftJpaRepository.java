package com.aquilabuddy.adapter.out.persistence.prdraft;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PR 초안 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface PullRequestDraftJpaRepository extends JpaRepository<PullRequestDraftJpaEntity, String> {

    /**
     * 최근 생성된 PR 초안을 먼저 반환합니다.
     */
    List<PullRequestDraftJpaEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 최근 생성된 PR 초안을 페이지 단위로 반환합니다.
     */
    Page<PullRequestDraftJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
