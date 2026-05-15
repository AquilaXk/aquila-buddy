package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;

/**
 * JPA entity를 application port가 요구하는 도메인 응답 모델로 변환합니다.
 */
final class RecommendedIssuePersistenceMapper {

    private RecommendedIssuePersistenceMapper() {
    }

    /**
     * 저장소 모델의 컬럼 값을 추천 이슈 도메인 모델로 옮깁니다.
     */
    static RecommendedIssue toDomain(RecommendedIssueJpaEntity entity) {
        return new RecommendedIssue(
            entity.getId(),
            entity.getRepoFullName(),
            entity.getIssueNumber(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getScore(),
            entity.getLabels(),
            entity.getStatus(),
            entity.getReason()
        );
    }

    /**
     * 검색 결과 도메인 모델을 fallback 저장소에 맞는 JPA entity로 변환합니다.
     */
    static RecommendedIssueJpaEntity toEntity(RecommendedIssue issue) {
        return new RecommendedIssueJpaEntity(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            issue.summary(),
            issue.score(),
            issue.labels(),
            issue.status(),
            issue.reason()
        );
    }
}
