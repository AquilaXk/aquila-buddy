package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;

final class RecommendedIssuePersistenceMapper {

    private RecommendedIssuePersistenceMapper() {
    }

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
}
