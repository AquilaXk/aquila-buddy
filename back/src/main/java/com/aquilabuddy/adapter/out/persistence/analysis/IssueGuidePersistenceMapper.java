package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;

/**
 * JPA entity를 application port가 요구하는 도메인 응답 모델로 변환합니다.
 */
final class IssueGuidePersistenceMapper {

    private IssueGuidePersistenceMapper() {
    }

    /**
     * 저장소 모델의 컬럼 값을 이슈 가이드 도메인 모델로 옮깁니다.
     */
    static IssueGuide toDomain(IssueGuideJpaEntity entity) {
        return new IssueGuide(
            entity.getIssueId(),
            entity.getFilePaths(),
            entity.getGuideline(),
            entity.getPseudoCode(),
            entity.getSideEffects()
        );
    }

    static CachedIssueGuide toCachedDomain(IssueGuideJpaEntity entity) {
        return new CachedIssueGuide(toDomain(entity), entity.getGeneratedAt());
    }

    static IssueGuideJpaEntity toEntity(IssueGuide guide) {
        return new IssueGuideJpaEntity(
            guide.issueKey(),
            guide.guideline(),
            guide.pseudoCode(),
            guide.filePaths(),
            guide.sideEffects()
        );
    }
}
