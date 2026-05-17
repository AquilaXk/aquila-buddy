package com.aquilabuddy.adapter.out.persistence.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;

/**
 * PR 초안 JPA entity와 도메인 모델 사이의 변환을 담당합니다.
 */
final class PullRequestDraftPersistenceMapper {

    private PullRequestDraftPersistenceMapper() {
    }

    static StoredPullRequestDraft toDomain(PullRequestDraftJpaEntity entity) {
        return new StoredPullRequestDraft(
            entity.getId(),
            entity.getUpstreamRepo(),
            entity.getGithubIssueNumber(),
            entity.getBaseBranch(),
            entity.getHeadBranch(),
            entity.getTitle(),
            entity.getBody(),
            entity.getSourceIssueId(),
            entity.getCreatedAt()
        );
    }

    static PullRequestDraftJpaEntity toEntity(
        String id,
        PullRequestDraftRequest request,
        PullRequestDraft draft,
        Instant createdAt
    ) {
        return new PullRequestDraftJpaEntity(
            id,
            request.upstreamRepo(),
            request.githubIssueNumber(),
            request.sourceIssueId(),
            request.baseBranch(),
            request.headBranch(),
            draft.title(),
            draft.body(),
            createdAt
        );
    }
}
