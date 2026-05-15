package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

/**
 * JPA entity를 application port가 요구하는 도메인 응답 모델로 변환합니다.
 */
final class IssueGuidePersistenceMapper {

    private IssueGuidePersistenceMapper() {
    }

    static IssueGuide toDomain(IssueGuideJpaEntity entity) {
        return new IssueGuide(
            entity.getIssueId(),
            entity.getGuideline(),
            entity.getPseudoCode(),
            entity.getSideEffects()
        );
    }
}
