package com.aquilabuddy.adapter.out.persistence.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 사용자별 이슈 guide 생성 quota와 요청 이력을 DB 기준으로 남기는 JPA 모델입니다.
 */
@Entity
@Table(name = "issue_guide_generation_requests")
public class IssueGuideGenerationRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_github_id", nullable = false)
    private Long userGithubId;

    @Column(name = "issue_id", nullable = false, length = 100)
    private String issueId;

    @Column(name = "requested_on", nullable = false)
    private LocalDate requestedOn;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IssueGuideGenerationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected IssueGuideGenerationRequestJpaEntity() {
    }

    private IssueGuideGenerationRequestJpaEntity(
        Long userGithubId,
        String issueId,
        LocalDate requestedOn,
        Instant requestedAt,
        IssueGuideGenerationStatus status
    ) {
        this.userGithubId = userGithubId;
        this.issueId = issueId;
        this.requestedOn = requestedOn;
        this.requestedAt = requestedAt;
        this.status = status;
    }

    static IssueGuideGenerationRequestJpaEntity acquired(
        long userGithubId,
        String issueId,
        LocalDate requestedOn,
        Instant requestedAt
    ) {
        return new IssueGuideGenerationRequestJpaEntity(
            userGithubId,
            issueId,
            requestedOn,
            requestedAt,
            IssueGuideGenerationStatus.ACQUIRED
        );
    }

    Long getRequestId() {
        return requestId;
    }

    Long getUserGithubId() {
        return userGithubId;
    }

    String getIssueId() {
        return issueId;
    }

    LocalDate getRequestedOn() {
        return requestedOn;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    IssueGuideGenerationStatus getStatus() {
        return status;
    }

    String getFailureReason() {
        return failureReason;
    }
}
