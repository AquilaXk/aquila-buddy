package com.aquilabuddy.adapter.out.persistence.issue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자별 추천 이슈 북마크와 분석 상태를 보관하는 JPA 엔티티입니다.
 */
@Entity
@Table(name = "user_recommended_issues")
@IdClass(UserRecommendedIssueId.class)
public class UserRecommendedIssueJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "issue_id", nullable = false, length = 100)
    private String issueId;

    @Column(name = "bookmarked", nullable = false)
    private boolean bookmarked;

    @Column(name = "bookmarked_at")
    private Instant bookmarkedAt;

    @Column(name = "analyzed", nullable = false)
    private boolean analyzed;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRecommendedIssueJpaEntity() {
    }

    UserRecommendedIssueJpaEntity(long userId, String issueId, Instant now) {
        this.userId = userId;
        this.issueId = issueId;
        this.bookmarked = false;
        this.analyzed = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markBookmarked(Instant now) {
        this.bookmarked = true;
        if (this.bookmarkedAt == null) {
            this.bookmarkedAt = now;
        }
        this.updatedAt = now;
    }

    void clearBookmark(Instant now) {
        this.bookmarked = false;
        this.bookmarkedAt = null;
        this.updatedAt = now;
    }

    void markAnalyzed(Instant now) {
        this.analyzed = true;
        this.analyzedAt = now;
        this.updatedAt = now;
    }

    Long getUserId() {
        return userId;
    }

    String getIssueId() {
        return issueId;
    }

    boolean isBookmarked() {
        return bookmarked;
    }

    Instant getBookmarkedAt() {
        return bookmarkedAt;
    }

    boolean isAnalyzed() {
        return analyzed;
    }

    Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
