package com.aquilabuddy.adapter.out.persistence.issue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 추천 이슈 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface RecommendedIssueJpaRepository extends JpaRepository<RecommendedIssueJpaEntity, String> {

    /**
     * 추천 점수가 높은 순서로 노출하고, 같은 점수에서는 이슈 번호가 낮은 항목을 먼저 반환합니다.
     */
    List<RecommendedIssueJpaEntity> findAllByOrderByScoreDescIssueNumberAsc();

    /**
     * GitHub 저장소 이름과 이슈 번호가 일치하는 추천 이슈를 조회합니다.
     */
    Optional<RecommendedIssueJpaEntity> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber);

    List<RecommendedIssueJpaEntity> findByIssueEmbeddingIsNullOrderByScoreDescIssueNumberAsc(Pageable pageable);

    long countByIssueEmbeddingIsNotNull();
}
