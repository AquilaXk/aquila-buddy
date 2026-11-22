package com.aquilabuddy.adapter.out.persistence.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.time.Instant;

/**
 * PR 초안 번역 JPA entity와 도메인 모델 사이의 변환을 담당합니다.
 */
final class PullRequestDraftTranslationPersistenceMapper {

    private PullRequestDraftTranslationPersistenceMapper() {
    }

    static PullRequestDraftTranslation toDomain(PullRequestDraftTranslationJpaEntity entity) {
        return new PullRequestDraftTranslation(
            entity.getId(),
            entity.getDraftId(),
            entity.getTargetLanguage(),
            entity.getTitle(),
            entity.getBody(),
            entity.getCreatedAt()
        );
    }

    static PullRequestDraftTranslationJpaEntity toEntity(
        String id,
        String draftId,
        String targetLanguage,
        PullRequestDraft translatedDraft,
        Instant createdAt
    ) {
        return new PullRequestDraftTranslationJpaEntity(
            id,
            draftId,
            targetLanguage,
            translatedDraft.title(),
            translatedDraft.body(),
            createdAt
        );
    }
}
