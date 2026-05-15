package com.aquilabuddy.adapter.out.persistence.prdraft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 저장된 PR 초안 번역 결과의 JPA 전용 모델입니다.
 */
@Entity
@Table(name = "pr_draft_translations")
public class PullRequestDraftTranslationJpaEntity {

    @Id
    @Column(name = "translation_id", nullable = false, length = 100)
    private String id;

    @Column(name = "draft_id", nullable = false, length = 100)
    private String draftId;

    @Column(name = "target_language", nullable = false, length = 20)
    private String targetLanguage;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", nullable = false, length = 8_000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PullRequestDraftTranslationJpaEntity() {
    }

    PullRequestDraftTranslationJpaEntity(
        String id,
        String draftId,
        String targetLanguage,
        String title,
        String body,
        Instant createdAt
    ) {
        this.id = id;
        this.draftId = draftId;
        this.targetLanguage = targetLanguage;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    String getId() {
        return id;
    }

    String getDraftId() {
        return draftId;
    }

    String getTargetLanguage() {
        return targetLanguage;
    }

    String getTitle() {
        return title;
    }

    String getBody() {
        return body;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
